package com.zentrapay.service;

import com.zentrapay.entity.*;
import com.zentrapay.exception.ResourceNotFoundException;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayoutQueryServiceTest {

    @Mock PayoutRepository payoutRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PayoutQueryService service;

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
    }

    private Payout samplePayout() {
        Payment payment = Payment.builder()
                .providerReference("ZP-test-1").amount(10_000L).currency("NGN")
                .status(PaymentStatus.COMPLETED).build();
        payment.setId(UUID.randomUUID());

        return Payout.builder()
                .id(UUID.randomUUID())
                .payment(payment)
                .reference("PO-ZP-test-1")
                .amount(9_900L)
                .currency("NGN")
                .status(PayoutStatus.PAID)
                .attempts(1)
                .build();
    }

    @Test
    void listMyPayoutsReturnsPage() {
        Payout payout = samplePayout();
        Page<Payout> page = new PageImpl<>(List.of(payout));
        when(payoutRepository.findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(
                currentUser.getId(), PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = service.listMyPayouts(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReference()).isEqualTo("PO-ZP-test-1");
    }

    @Test
    void listMyPayoutsReturnsEmptyPageWhenNone() {
        Page<Payout> empty = new PageImpl<>(List.of());
        when(payoutRepository.findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(
                currentUser.getId(), PageRequest.of(0, 20)))
                .thenReturn(empty);

        var result = service.listMyPayouts(PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getMyPayoutReturnsCorrectPayout() {
        Payout payout = samplePayout();
        when(payoutRepository.findByIdAndPaymentPaymentLinkUserId(payout.getId(), currentUser.getId()))
                .thenReturn(Optional.of(payout));

        var result = service.getMyPayout(payout.getId());

        assertThat(result.getReference()).isEqualTo("PO-ZP-test-1");
        assertThat(result.getAmount()).isEqualTo(9_900L);
        assertThat(result.getStatus()).isEqualTo("PAID");
    }

    @Test
    void getMyPayoutThrowsForNonexistentId() {
        UUID fakeId = UUID.randomUUID();
        when(payoutRepository.findByIdAndPaymentPaymentLinkUserId(fakeId, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyPayout(fakeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
