package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.VerificationResult;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Confirms payments authoritatively and settles funds to the seller.
 *
 * This is the single place that decides a payment succeeded — reached from both
 * the browser callback and the provider webhook. It is idempotent: a payment
 * already COMPLETED is never processed (or paid out) twice, so duplicate
 * webhooks and a callback racing a webhook are both safe.
 *
 * On success we:
 * 1. Re-verify with the provider (never trust the redirect/webhook body alone).
 * 2. Mark the payment COMPLETED and bump the link's usage counters.
 * 3. Pay the seller their amount minus the platform fee.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentConfirmationService {

    private final PaymentRepository paymentRepository;
    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentProvider paymentProvider;
    private final PayoutService payoutService;

    /**
     * Confirm a payment by our reference. Returns the resulting payment status
     * name. Safe to call repeatedly.
     */
    @Transactional
    public String confirmByReference(String reference) {
        if (reference == null || reference.isBlank()) {
            throw new IllegalArgumentException("Payment reference is required");
        }

        Payment payment = paymentRepository.findByProviderReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Unknown payment reference"));

        // Idempotency: if we already settled this payment, do nothing further.
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Payment {} already completed; skipping", reference);
            return payment.getStatus().name();
        }

        VerificationResult verification = paymentProvider.verify(reference);

        if (!verification.isSuccess()) {
            // Only downgrade a still-pending payment; never overwrite COMPLETED.
            if (verification.status() == com.zentrapay.provider.ProviderStatus.FAILED
                    || verification.status() == com.zentrapay.provider.ProviderStatus.ABANDONED) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
            log.info("Payment {} not successful (provider status: {})",
                    reference, verification.rawStatus());
            return payment.getStatus().name();
        }

        // Defense in depth: the paid amount/currency must match what we asked for.
        if (verification.amount() != payment.getAmount()
                || !verification.currency().equalsIgnoreCase(payment.getCurrency())) {
            log.error("Payment {} amount/currency mismatch: expected {} {}, provider reported {} {}",
                    reference, payment.getAmount(), payment.getCurrency(),
                    verification.amount(), verification.currency());
            throw new IllegalStateException("Payment amount mismatch; refusing to settle.");
        }

        markCompleted(payment);
        // Settlement is durable and retryable: PayoutService persists a payout
        // record and attempts it, so a provider failure never loses the money
        // owed — reconciliation retries it out of band.
        payoutService.createAndAttempt(payment);
        return PaymentStatus.COMPLETED.name();
    }

    private void markCompleted(Payment payment) {
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        PaymentLink link = payment.getPaymentLink();
        link.setCurrentUses(link.getCurrentUses() + 1);
        if (Boolean.TRUE.equals(link.getSingleUse())
                || (link.getMaxUses() != null && link.getCurrentUses() >= link.getMaxUses())) {
            link.setStatus(PaymentLinkStatus.PAID);
        }
        paymentLinkRepository.save(link);
        log.info("Payment {} marked COMPLETED", payment.getProviderReference());
    }

}
