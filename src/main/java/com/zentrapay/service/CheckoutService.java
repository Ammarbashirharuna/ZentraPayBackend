package com.zentrapay.service;

import com.zentrapay.dto.checkout.InitiatePaymentRequest;
import com.zentrapay.dto.checkout.InitiatePaymentResponse;
import com.zentrapay.dto.checkout.PublicPaymentLinkResponse;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentStatus;
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
import java.util.UUID;

/**
 * Public checkout: what unauthenticated customers hit when they open a payment
 * link and pay it.
 *
 * Security stance: the amount and currency are always read from the stored
 * link, never from the customer's request, so a customer cannot pay a different
 * amount. We create our own {@link Payment} with a unique reference before
 * calling the provider, and confirmation happens server-side (verify/webhook),
 * never on the browser redirect alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CheckoutService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    /**
     * How long a PENDING payment stays reusable for the same (link, customer).
     * A repeat submit within this window reuses the existing payment instead of
     * creating a duplicate; after it, we start fresh so a stale/abandoned
     * attempt never traps the customer.
     */
    @Value("${checkout.reuse-window-minutes:30}")
    private long reuseWindowMinutes;

    /** Public link view for rendering the checkout page. */
    public PublicPaymentLinkResponse getPublicLink(String shortCode) {
        PaymentLink link = paymentLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));
        return PublicPaymentLinkResponse.from(link);
    }

    /**
     * Start a payment: validate the link is payable, create a PENDING payment
     * with a unique reference, and ask the provider for a checkout URL.
     */
    @Transactional
    public InitiatePaymentResponse initiatePayment(String shortCode, InitiatePaymentRequest request) {
        PaymentLink link = paymentLinkRepository.findByShortCode(shortCode)
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));

        if (!link.isPayable()) {
            throw new IllegalStateException("This payment link is no longer accepting payments.");
        }

        // Idempotency: reuse a recent open payment for the same (link, customer)
        // so a double-submit doesn't create duplicate PENDING rows. The
        // reference doubles as the provider idempotency key, so re-initializing
        // with it returns a checkout for the same intent, not a second charge.
        Payment payment = findReusablePayment(link.getId(), request.getCustomerEmail());
        String reference;
        if (payment != null) {
            reference = payment.getProviderReference();
            log.info("Reusing open payment {} for link {} ({})",
                    reference, shortCode, request.getCustomerEmail());
        } else {
            reference = "ZP-" + UUID.randomUUID().toString().replace("-", "");
            payment = Payment.builder()
                    .paymentLink(link)
                    .customerEmail(request.getCustomerEmail())
                    .amount(link.getAmount())
                    .currency(link.getCurrency())
                    .providerReference(reference)
                    .status(PaymentStatus.PENDING)
                    .build();
            paymentRepository.save(payment);
        }

        String callbackUrl = base() + "/api/v1/pay/callback";

        InitializeResult result = paymentProvider.initialize(InitializeRequest.builder()
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .email(request.getCustomerEmail())
                .reference(reference)
                .redirectUrl(callbackUrl)
                .build());

        log.info("Initiated payment {} for link {} ({} {})",
                reference, shortCode, link.getAmount(), link.getCurrency());

        return InitiatePaymentResponse.builder()
                .reference(reference)
                .checkoutUrl(result.checkoutUrl())
                .accessCode(result.accessCode())
                .build();
    }

    /**
     * The most recent still-open payment for this (link, customer) if it is
     * within the reuse window; otherwise null so the caller starts fresh.
     */
    private Payment findReusablePayment(UUID linkId, String customerEmail) {
        return paymentRepository
                .findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        linkId, customerEmail, PaymentStatus.PENDING)
                .filter(p -> p.getCreatedAt() != null
                        && p.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(reuseWindowMinutes)))
                .orElse(null);
    }

    private String base() {
        return appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }
}
