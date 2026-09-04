package com.zentrapay.service;

import com.zentrapay.dto.checkout.InitiatePaymentRequest;
import com.zentrapay.dto.checkout.InitiatePaymentResponse;
import com.zentrapay.dto.checkout.PublicPaymentLinkResponse;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.exception.BusinessRuleException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.provider.InitializeRequest;
import com.zentrapay.provider.InitializeResult;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    @Value("${app.idempotency-window-minutes:5}")
    private int idempotencyWindowMinutes;

    public PublicPaymentLinkResponse getPublicPaymentLink(String shortCode) {
        PaymentLink link = paymentLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));
        validateLinkUsable(link);
        return PublicPaymentLinkResponse.from(link);
    }

    @Transactional
    public InitiatePaymentResponse initiatePayment(String shortCode, InitiatePaymentRequest request) {
        PaymentLink link = paymentLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));

        validateLinkUsable(link);

        // Idempotency: reuse recent PENDING payment for same email+link
        Optional<Payment> existing = paymentRepository
                .findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        link.getId(), request.getCustomerEmail(), PaymentStatus.PENDING);

        if (existing.isPresent()) {
            Payment payment = existing.get();
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(idempotencyWindowMinutes);
            if (payment.getCreatedAt().isAfter(windowStart)) {
                log.info("Reusing existing PENDING payment {} for {}", payment.getId(), request.getCustomerEmail());
                InitializeResult providerResult = paymentProvider.initialize(InitializeRequest.builder()
                        .reference(payment.getProviderReference())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .email(payment.getCustomerEmail())
                        .redirectUrl(link.getRedirectUrl())
                        .build());
                return InitiatePaymentResponse.builder()
                        .reference(payment.getProviderReference())
                        .checkoutUrl(providerResult.checkoutUrl())
                        .accessCode(providerResult.accessCode())
                        .build();
            }
        }

        // Create new payment
        String reference = "ZR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .paymentLink(link)
                .customerEmail(request.getCustomerEmail())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .providerReference(reference)
                .status(PaymentStatus.PENDING)
                .build();

        InitializeResult providerResult = paymentProvider.initialize(InitializeRequest.builder()
                .reference(reference)
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .email(request.getCustomerEmail())
                .redirectUrl(link.getRedirectUrl())
                .build());

        paymentRepository.save(payment);

        link.setCurrentUses(link.getCurrentUses() + 1);
        if (Boolean.TRUE.equals(link.getSingleUse())) {
            link.setStatus(PaymentLinkStatus.PAID);
        }
        paymentLinkRepository.save(link);

        log.info("Payment initiated: {} for link {}", payment.getId(), link.getShortCode());

        return InitiatePaymentResponse.builder()
                .reference(reference)
                .checkoutUrl(providerResult.checkoutUrl())
                .accessCode(providerResult.accessCode())
                .build();
    }

    private void validateLinkUsable(PaymentLink link) {
        if (link.getStatus() != PaymentLinkStatus.ACTIVE) {
            throw new BusinessRuleException("LINK_INACTIVE", "This payment link is no longer accepting payments.");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("LINK_EXPIRED", "This payment link has expired.");
        }
        if (link.getMaxUses() != null && link.getCurrentUses() >= link.getMaxUses()) {
            throw new BusinessRuleException("LINK_MAX_USES_REACHED", "This payment link has reached its maximum usage.");
        }
    }
}
