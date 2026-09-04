package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.exception.BusinessRuleException;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.provider.ProviderStatus;
import com.zentrapay.provider.VerificationResult;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmationServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentLinkRepository paymentLinkRepository;
    @Mock PaymentProvider paymentProvider;
    @Mock PayoutService payoutService;
    @Mock EmailService emailService;

    @InjectMocks PaymentConfirmationService service;

    private Payment pendingPayment(long amount, String currency) {
        PayoutAccount account = PayoutAccount.builder()
                .accountNumber("0123456789")
                .accountName("Ada Seller")
                .bankCode("058")
                .method(PayoutMethod.BANK_ACCOUNT)
                .build();
        PaymentLink link = PaymentLink.builder()
                .shortCode("ABC1234")
                .payoutAccount(account)
                .amount(amount)
                .currency(currency)
                .status(PaymentLinkStatus.ACTIVE)
                .singleUse(false)
                .currentUses(0)
                .build();
        return Payment.builder()
                .paymentLink(link)
                .customerEmail("buyer@example.com")
                .amount(amount)
                .currency(currency)
                .providerReference("ZP-ref-1")
                .status(PaymentStatus.PENDING)
                .build();
    }

    @Test
    void confirmsSuccessfulPaymentAndDelegatesSettlement() {
        Payment payment = pendingPayment(10_000L, "NGN");
        when(paymentRepository.findByProviderReference("ZP-ref-1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verify("ZP-ref-1")).thenReturn(VerificationResult.builder()
                .status(ProviderStatus.SUCCESS).amount(10_000L).currency("NGN")
                .reference("ZP-ref-1").rawStatus("success").build());

        String result = service.confirmByReference("ZP-ref-1");

        assertThat(result).isEqualTo("COMPLETED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getPaidAt()).isNotNull();
        // Settlement is delegated to the durable, retryable payout service.
        verify(payoutService).createAndAttempt(payment);
    }

    @Test
    void isIdempotentWhenAlreadyCompleted() {
        Payment payment = pendingPayment(10_000L, "NGN");
        payment.setStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findByProviderReference("ZP-ref-1")).thenReturn(Optional.of(payment));

        String result = service.confirmByReference("ZP-ref-1");

        assertThat(result).isEqualTo("COMPLETED");
        verify(paymentProvider, never()).verify(anyString());
        verify(payoutService, never()).createAndAttempt(any());
    }

    @Test
    void refusesToSettleOnAmountMismatch() {
        Payment payment = pendingPayment(10_000L, "NGN");
        when(paymentRepository.findByProviderReference("ZP-ref-1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verify("ZP-ref-1")).thenReturn(VerificationResult.builder()
                .status(ProviderStatus.SUCCESS).amount(500L).currency("NGN")
                .reference("ZP-ref-1").rawStatus("success").build());

        assertThatThrownBy(() -> service.confirmByReference("ZP-ref-1"))
                .isInstanceOf(BusinessRuleException.class);
        verify(payoutService, never()).createAndAttempt(any());
    }

    @Test
    void marksFailedWhenProviderReportsFailure() {
        Payment payment = pendingPayment(10_000L, "NGN");
        when(paymentRepository.findByProviderReference("ZP-ref-1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verify("ZP-ref-1")).thenReturn(VerificationResult.builder()
                .status(ProviderStatus.FAILED).amount(0L).currency("NGN")
                .reference("ZP-ref-1").rawStatus("failed").build());

        String result = service.confirmByReference("ZP-ref-1");

        assertThat(result).isEqualTo("FAILED");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(payoutService, never()).createAndAttempt(any());
    }

    @Test
    void singleUseLinkBecomesPaid() {
        Payment payment = pendingPayment(10_000L, "NGN");
        payment.getPaymentLink().setSingleUse(true);
        when(paymentRepository.findByProviderReference("ZP-ref-1")).thenReturn(Optional.of(payment));
        when(paymentProvider.verify("ZP-ref-1")).thenReturn(VerificationResult.builder()
                .status(ProviderStatus.SUCCESS).amount(10_000L).currency("NGN")
                .reference("ZP-ref-1").rawStatus("success").build());

        service.confirmByReference("ZP-ref-1");

        assertThat(payment.getPaymentLink().getStatus()).isEqualTo(PaymentLinkStatus.PAID);
        assertThat(payment.getPaymentLink().getCurrentUses()).isEqualTo(1);
    }
}
