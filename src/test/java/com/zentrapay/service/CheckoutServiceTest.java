package com.zentrapay.service;

import com.zentrapay.dto.checkout.InitiatePaymentRequest;
import com.zentrapay.dto.checkout.InitiatePaymentResponse;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.provider.InitializeRequest;
import com.zentrapay.provider.InitializeResult;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock PaymentLinkRepository paymentLinkRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock PaymentProvider paymentProvider;

    @InjectMocks CheckoutService service;

    private final UUID linkId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "idempotencyWindowMinutes", 5);
    }

    private PaymentLink payableLink() {
        PaymentLink link = PaymentLink.builder()
                .shortCode("ABC1234")
                .amount(10_000L)
                .currency("NGN")
                .status(PaymentLinkStatus.ACTIVE)
                .currentUses(0)
                .build();
        link.setId(linkId);
        return link;
    }

    private void stubProviderInit() {
        when(paymentProvider.initialize(any(InitializeRequest.class)))
                .thenReturn(new InitializeResult("https://pay.test/checkout", "acc-1", "ZP-x"));
    }

    @Test
    void createsNewPaymentWhenNoOpenPaymentExists() {
        when(paymentLinkRepository.findByShortCode("ABC1234")).thenReturn(Optional.of(payableLink()));
        when(paymentRepository
                .findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        linkId, "buyer@example.com", PaymentStatus.PENDING))
                .thenReturn(Optional.empty());
        stubProviderInit();

        InitiatePaymentResponse response = service.initiatePayment("ABC1234",
                new InitiatePaymentRequest("buyer@example.com"));

        assertThat(response.getCheckoutUrl()).isEqualTo("https://pay.test/checkout");
        assertThat(response.getReference()).startsWith("ZR-");
        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void reusesRecentOpenPaymentInsteadOfCreatingDuplicate() {
        Payment existing = Payment.builder()
                .customerEmail("buyer@example.com")
                .amount(10_000L)
                .currency("NGN")
                .providerReference("ZP-existing")
                .status(PaymentStatus.PENDING)
                .build();
        existing.setCreatedAt(LocalDateTime.now().minusMinutes(2));

        when(paymentLinkRepository.findByShortCode("ABC1234")).thenReturn(Optional.of(payableLink()));
        when(paymentRepository
                .findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        linkId, "buyer@example.com", PaymentStatus.PENDING))
                .thenReturn(Optional.of(existing));
        stubProviderInit();

        InitiatePaymentResponse response = service.initiatePayment("ABC1234",
                new InitiatePaymentRequest("buyer@example.com"));

        assertThat(response.getReference()).isEqualTo("ZP-existing");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void startsFreshWhenOpenPaymentIsOutsideReuseWindow() {
        Payment stale = Payment.builder()
                .customerEmail("buyer@example.com")
                .amount(10_000L)
                .currency("NGN")
                .providerReference("ZP-stale")
                .status(PaymentStatus.PENDING)
                .build();
        stale.setCreatedAt(LocalDateTime.now().minusMinutes(45));

        when(paymentLinkRepository.findByShortCode("ABC1234")).thenReturn(Optional.of(payableLink()));
        when(paymentRepository
                .findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        linkId, "buyer@example.com", PaymentStatus.PENDING))
                .thenReturn(Optional.of(stale));
        stubProviderInit();

        InitiatePaymentResponse response = service.initiatePayment("ABC1234",
                new InitiatePaymentRequest("buyer@example.com"));

        assertThat(response.getReference()).isNotEqualTo("ZP-stale");
        verify(paymentRepository).save(any(Payment.class));
    }
}
