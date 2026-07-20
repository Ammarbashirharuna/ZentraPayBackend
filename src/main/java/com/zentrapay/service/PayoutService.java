package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.Payout;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.entity.PayoutStatus;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.PayoutRequest;
import com.zentrapay.provider.PayoutResult;
import com.zentrapay.provider.ProviderStatus;
import com.zentrapay.repository.PayoutRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owns seller settlements: creating a payout record for a confirmed payment,
 * attempting the transfer with the provider, and updating payout state.
 *
 * The payout record is the durable source of truth. Creating it is separate
 * from attempting it, so a provider outage never loses a settlement — the
 * record survives as PENDING/FAILED and {@code PayoutReconciliationJob} retries
 * it later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutService {

    private final PayoutRepository payoutRepository;
    private final PaymentProvider paymentProvider;

    @Value("${platform.fee-basis-points:100}")
    private long feeBasisPoints;

    @Value("${cashonrails.sender-name:ZentraPay}")
    private String senderName;

    /**
     * Create the payout record for a freshly confirmed payment (idempotent: if
     * one already exists for this payment, returns it), then attempt it once.
     * Never throws — a failure leaves the record retryable.
     */
    @Transactional
    public void createAndAttempt(Payment payment) {
        Payout payout = payoutRepository.findByPaymentId(payment.getId())
                .orElseGet(() -> createRecord(payment));
        if (payout == null) {
            return; // couldn't create (e.g. no payout account); already logged
        }
        // Only attempt if there is still work to do.
        if (payout.getStatus() == PayoutStatus.PENDING || payout.getStatus() == PayoutStatus.FAILED) {
            attempt(payout);
        }
    }

    /** Create the durable payout record from the payment. */
    private Payout createRecord(Payment payment) {
        PaymentLink link = payment.getPaymentLink();
        PayoutAccount account = link.getPayoutAccount();
        if (account == null) {
            log.error("Payment {} has no payout account; cannot create payout",
                    payment.getProviderReference());
            return null;
        }

        long fee = payment.getAmount() * feeBasisPoints / 10_000L;
        long net = payment.getAmount() - fee;
        if (net <= 0) {
            log.error("Non-positive net ({}) for payment {}; skipping payout",
                    net, payment.getProviderReference());
            return null;
        }

        Payout payout = Payout.builder()
                .payment(payment)
                .payoutAccount(account)
                .reference("PO-" + payment.getProviderReference())
                .amount(net)
                .currency(payment.getCurrency())
                .status(PayoutStatus.PENDING)
                .attempts(0)
                .build();
        return payoutRepository.save(payout);
    }

    /**
     * Attempt the transfer with the provider and record the outcome. Never
     * throws; a provider error marks the payout FAILED for later retry.
     */
    @Transactional
    public void attempt(Payout payout) {
        PayoutAccount account = payout.getPayoutAccount();
        payout.setAttempts(payout.getAttempts() + 1);
        payout.setLastAttemptAt(LocalDateTime.now());
        payout.setStatus(PayoutStatus.PROCESSING);
        payoutRepository.save(payout);

        try {
            PayoutResult result = paymentProvider.payout(PayoutRequest.builder()
                    .accountNumber(account.getAccountNumber())
                    .accountName(account.getAccountName())
                    .bankCode(account.getBankCode())
                    .amount(payout.getAmount())
                    .currency(payout.getCurrency())
                    .senderName(senderName)
                    .narration("ZentraPay settlement " + payout.getReference())
                    .reference(payout.getReference())
                    .type(payoutTypeHint(account))
                    .build());

            payout.setProviderReference(result.providerReference());
            payout.setLastError(null);

            if (result.status() == ProviderStatus.SUCCESS) {
                payout.setStatus(PayoutStatus.PAID);
                payout.setCompletedAt(LocalDateTime.now());
            } else if (result.status() == ProviderStatus.FAILED
                    || result.status() == ProviderStatus.ABANDONED) {
                payout.setStatus(PayoutStatus.FAILED);
                payout.setLastError("Provider reported: " + result.rawStatus());
            } else {
                // PENDING/UNKNOWN: accepted, awaiting confirmation via webhook.
                payout.setStatus(PayoutStatus.PROCESSING);
            }
            payoutRepository.save(payout);
            log.info("Payout {} attempt {} -> {} (provider status {})",
                    payout.getReference(), payout.getAttempts(),
                    payout.getStatus(), result.rawStatus());
        } catch (RuntimeException ex) {
            payout.setStatus(PayoutStatus.FAILED);
            payout.setLastError(ex.getMessage());
            payoutRepository.save(payout);
            log.error("Payout {} attempt {} failed: {}",
                    payout.getReference(), payout.getAttempts(), ex.getMessage());
        }
    }

    /**
     * Apply a transfer status update from a webhook to the matching payout.
     * Matches on our reference or the provider's reference. Idempotent.
     */
    @Transactional
    public void applyTransferStatus(String reference, ProviderStatus status, String rawStatus) {
        if (reference == null || reference.isBlank()) {
            return;
        }
        Payout payout = payoutRepository.findByReference(reference)
                .or(() -> payoutRepository.findByProviderReference(reference))
                .orElse(null);
        if (payout == null) {
            log.warn("Transfer webhook for unknown payout reference {}", reference);
            return;
        }
        if (payout.getStatus() == PayoutStatus.PAID) {
            return; // already final
        }
        switch (status) {
            case SUCCESS -> {
                payout.setStatus(PayoutStatus.PAID);
                payout.setCompletedAt(LocalDateTime.now());
                payout.setLastError(null);
            }
            case FAILED, ABANDONED -> {
                payout.setStatus(PayoutStatus.FAILED);
                payout.setLastError("Transfer webhook: " + rawStatus);
            }
            default -> payout.setStatus(PayoutStatus.PROCESSING);
        }
        payoutRepository.save(payout);
        log.info("Payout {} updated from transfer webhook -> {}", payout.getReference(), payout.getStatus());
    }

    private String payoutTypeHint(PayoutAccount account) {
        if (account.getMethod() == PayoutMethod.MOBILE_MONEY) {
            return "MOBILE_MONEY";
        }
        if (account.getMethod() == PayoutMethod.EFT) {
            return "EFT";
        }
        return null;
    }
}
