package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import com.zentrapay.repository.PayoutRepository;
import com.zentrapay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PayoutRepository payoutRepository;
    @Mock PaymentLinkRepository paymentLinkRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PaymentQueryService service;

    private User currentUser;

    @BeforeEach
    void setUp() {
        currentUser = User.builder().id(UUID.randomUUID()).email("seller@test.com").build();
        SecurityContext ctx = mock(SecurityContext.class);
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("seller@test.com");
        when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
        when(userRepository.findByEmailIgnoreCase("seller@test.com")).thenReturn(Optional.of(currentUser));
        ReflectionTestUtils.setField(service, "feeBasisPoints", 100L);
    }

    private Payment samplePayment() {
        PaymentLink link = PaymentLink.builder()
                .shortCode("ABC1234").title("Test").amount(10_000L).currency("NGN").build();
        link.setId(UUID.randomUUID());
        Payment p = Payment.builder()
                .paymentLink(link).customerEmail("buyer@example.com")
                .amount(10_000L).currency("NGN").providerReference("ZP-test-1")
                .status(PaymentStatus.COMPLETED).build();
        p.setId(UUID.randomUUID());
        return p;
    }

    @Test
    void listMyPaymentsReturnsPage() {
        Payment payment = samplePayment();
        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.findByPaymentLinkUserIdOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = service.listMyPayments(null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getAmount()).isEqualTo(10_000L);
    }

    @Test
    void listMyPaymentsFiltersByStatus() {
        Payment payment = samplePayment();
        Page<Payment> page = new PageImpl<>(List.of(payment));
        when(paymentRepository.findByPaymentLinkUserIdAndStatusOrderByCreatedAtDesc(
                currentUser.getId(), PaymentStatus.COMPLETED, PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = service.listMyPayments(PaymentStatus.COMPLETED, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void getMyPaymentReturnsCorrectPayment() {
        Payment payment = samplePayment();
        when(paymentRepository.findByIdAndPaymentLinkUserId(payment.getId(), currentUser.getId()))
                .thenReturn(Optional.of(payment));

        var result = service.getMyPayment(payment.getId());

        assertThat(result.getProviderReference()).isEqualTo("ZP-test-1");
        assertThat(result.getAmount()).isEqualTo(10_000L);
    }

    @Test
    void getMyPaymentThrowsForNonexistentId() {
        UUID fakeId = UUID.randomUUID();
        when(paymentRepository.findByIdAndPaymentLinkUserId(fakeId, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyPayment(fakeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMySummaryReturnsEmptyWhenNoPayments() {
        when(paymentRepository.aggregateCompletedByCurrency(currentUser.getId())).thenReturn(List.of());
        when(paymentRepository.countPendingByCurrency(currentUser.getId())).thenReturn(List.of());
        when(paymentRepository.countFailedByCurrency(currentUser.getId())).thenReturn(List.of());
        when(payoutRepository.countPayoutsByCurrencyAndStatus(currentUser.getId())).thenReturn(List.of());

        var result = service.getMySummary();

        assertThat(result.getTotalGrossCollected()).isZero();
        assertThat(result.getTotalPaymentsCount()).isZero();
        assertThat(result.getCurrencies()).isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void getMySummaryAggregatesCorrectly() {
        Object[] completedRow = new Object[]{"NGN", 20_000L, 2L};
        List<Object[]> completedList = new java.util.ArrayList<>();
        completedList.add(completedRow);
        when(paymentRepository.aggregateCompletedByCurrency(currentUser.getId()))
                .thenReturn(completedList);
        when(paymentRepository.countPendingByCurrency(currentUser.getId()))
                .thenReturn(new java.util.ArrayList<>());
        when(paymentRepository.countFailedByCurrency(currentUser.getId()))
                .thenReturn(new java.util.ArrayList<>());
        when(payoutRepository.countPayoutsByCurrencyAndStatus(currentUser.getId()))
                .thenReturn(new java.util.ArrayList<>());

        var result = service.getMySummary();

        assertThat(result.getTotalGrossCollected()).isEqualTo(20_000L);
        assertThat(result.getTotalPlatformFees()).isEqualTo(200L);
        assertThat(result.getTotalNetPaid()).isEqualTo(19_800L);
        assertThat(result.getTotalPaymentsCount()).isEqualTo(2L);
        assertThat(result.getCurrencies()).hasSize(1);
        assertThat(result.getCurrencies().get(0).getCurrency()).isEqualTo("NGN");
    }

    @Test
    void getMyAnalyticsReturnsEmptyWhenNoData() {
        when(paymentRepository.dailyRevenueSince(eq(currentUser.getId()), any())).thenReturn(List.of());
        when(paymentRepository.perLinkStats(currentUser.getId())).thenReturn(List.of());
        when(paymentRepository.countByPaymentLinkUserIdAndStatus(currentUser.getId(), PaymentStatus.COMPLETED)).thenReturn(0L);
        when(paymentRepository.countByPaymentLinkUserId(currentUser.getId())).thenReturn(0L);
        when(paymentRepository.averagePaymentAmount(currentUser.getId())).thenReturn(null);

        var result = service.getMyAnalytics();

        assertThat(result.getDailyRevenue()).isEmpty();
        assertThat(result.getLinkAnalytics()).isEmpty();
        assertThat(result.getOverallConversionRate()).isZero();
    }
}
