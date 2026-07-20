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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    @Mock PayoutRepository payoutRepository;
    @Mock PaymentProvider paymentProvider;

    @InjectMocks PayoutService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "feeBasisPoints", 100L); // 1%
        ReflectionTestUtils.setField(service, "senderName", "ZentraPay");
    }

    private Payment payment(long amount) {
        PayoutAccount account = PayoutAccount.builder()
                .accountNumber("0123456789").accountName("Ada Seller")
                .bankCode("058").method(PayoutMethod.BANK_ACCOUNT).build();
        PaymentLink link = PaymentLink.builder().shortCode("ABC1234")
                .payoutAccount(account).build();
        Payment p = Payment.builder()
                .paymentLink(link).amount(amount).currency("NGN")
                .providerReference("ZP-ref-1").build();
        p.setId(UUID.randomUUID());
        return p;
    }

    @Test
    void createsPayoutNetOfFeeAndAttempts() {
        Payment payment = payment(10_000L);
        when(payoutRepository.findByPaymentId(payment.getId())).thenReturn(Optional.empty());
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.payout(any())).thenReturn(PayoutResult.builder()
                .status(ProviderStatus.SUCCESS).providerReference("cor-1").rawStatus("success").build());

        service.createAndAttempt(payment);

        ArgumentCaptor<PayoutRequest> captor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(paymentProvider).payout(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(9_900L); // 1% fee
        assertThat(captor.getValue().reference()).isEqualTo("PO-ZP-ref-1");
    }

    @Test
    void attemptMarksPaidOnProviderSuccess() {
        Payout payout = Payout.builder()
                .payoutAccount(payment(10_000L).getPaymentLink().getPayoutAccount())
                .reference("PO-ZP-ref-1").amount(9_900L).currency("NGN")
                .status(PayoutStatus.PENDING).attempts(0).build();
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.payout(any())).thenReturn(PayoutResult.builder()
                .status(ProviderStatus.SUCCESS).providerReference("cor-1").rawStatus("success").build());

        service.attempt(payout);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(payout.getCompletedAt()).isNotNull();
        assertThat(payout.getAttempts()).isEqualTo(1);
    }

    @Test
    void attemptMarksFailedWhenProviderThrows() {
        Payout payout = Payout.builder()
                .payoutAccount(payment(10_000L).getPaymentLink().getPayoutAccount())
                .reference("PO-ZP-ref-1").amount(9_900L).currency("NGN")
                .status(PayoutStatus.PENDING).attempts(0).build();
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));
        when(paymentProvider.payout(any())).thenThrow(new RuntimeException("provider down"));

        service.attempt(payout);

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
        assertThat(payout.getLastError()).contains("provider down");
        assertThat(payout.getAttempts()).isEqualTo(1);
    }

    @Test
    void transferWebhookMarksPayoutPaid() {
        Payout payout = Payout.builder()
                .reference("PO-ZP-ref-1").amount(9_900L).currency("NGN")
                .status(PayoutStatus.PROCESSING).attempts(1).build();
        when(payoutRepository.findByReference("PO-ZP-ref-1")).thenReturn(Optional.of(payout));
        when(payoutRepository.save(any(Payout.class))).thenAnswer(inv -> inv.getArgument(0));

        service.applyTransferStatus("PO-ZP-ref-1", ProviderStatus.SUCCESS, "success");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        assertThat(payout.getCompletedAt()).isNotNull();
    }

    @Test
    void transferWebhookIgnoresAlreadyPaidPayout() {
        Payout payout = Payout.builder()
                .reference("PO-ZP-ref-1").status(PayoutStatus.PAID).attempts(1).build();
        when(payoutRepository.findByReference("PO-ZP-ref-1")).thenReturn(Optional.of(payout));

        service.applyTransferStatus("PO-ZP-ref-1", ProviderStatus.FAILED, "failed");

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PAID);
        verify(payoutRepository, never()).save(any());
    }

    @Test
    void doesNotReattemptExistingPaidPayout() {
        Payment payment = payment(10_000L);
        Payout existing = Payout.builder()
                .reference("PO-ZP-ref-1").status(PayoutStatus.PAID).attempts(1).build();
        when(payoutRepository.findByPaymentId(payment.getId())).thenReturn(Optional.of(existing));

        service.createAndAttempt(payment);

        verify(paymentProvider, never()).payout(any());
    }
}
