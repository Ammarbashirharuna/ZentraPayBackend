package com.zentrapay.service;

import com.zentrapay.dto.paymentlink.CreatePaymentLinkRequest;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.User;
import com.zentrapay.exception.BusinessRuleException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PayoutAccountRepository;
import com.zentrapay.repository.PaymentLinkRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLinkServiceTest {

    @Mock PaymentLinkRepository paymentLinkRepository;
    @Mock PayoutAccountRepository payoutAccountRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PaymentLinkService service;

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
        ReflectionTestUtils.setField(service, "appBaseUrl", "http://localhost:8080");
    }

    private PayoutAccount activeValidatedAccount() {
        return PayoutAccount.builder()
                .id(UUID.randomUUID())
                .user(currentUser)
                .accountValidated(true)
                .isActive(true)
                .accountNumber("0123456789")
                .bankCode("058")
                .currency("NGN")
                .country("NG")
                .method(PayoutMethod.BANK_ACCOUNT)
                .bankName("Test Bank")
                .accountName("Test Seller")
                .build();
    }

    @Test
    void createPaymentLinkRequiresActivePayoutAccount() {
        when(payoutAccountRepository.findByUserIdAndIsActiveTrue(currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPaymentLink(
                CreatePaymentLinkRequest.builder().title("Test").amount(1000L).currency("NGN").build()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("payout account");
    }

    @Test
    void createPaymentLinkRequiresValidatedAccount() {
        PayoutAccount account = activeValidatedAccount();
        account.setAccountValidated(false);
        when(payoutAccountRepository.findByUserIdAndIsActiveTrue(currentUser.getId()))
                .thenReturn(Optional.of(account));

        assertThatThrownBy(() -> service.createPaymentLink(
                CreatePaymentLinkRequest.builder().title("Test").amount(1000L).currency("NGN").build()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not validated");
    }

    @Test
    void createPaymentLinkSucceedsWithValidAccount() {
        PayoutAccount account = activeValidatedAccount();
        when(payoutAccountRepository.findByUserIdAndIsActiveTrue(currentUser.getId()))
                .thenReturn(Optional.of(account));
        when(paymentLinkRepository.existsByShortCode(anyString())).thenReturn(false);
        when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(inv -> {
            PaymentLink link = inv.getArgument(0);
            link.setId(UUID.randomUUID());
            return link;
        });

        var response = service.createPaymentLink(
                CreatePaymentLinkRequest.builder()
                        .title("Coffee").amount(5000L).currency("NGN").build());

        assertThat(response.getTitle()).isEqualTo("Coffee");
        assertThat(response.getAmount()).isEqualTo(5000L);
        assertThat(response.getPaymentUrl()).contains("/api/v1/pay/");
        verify(paymentLinkRepository).save(any(PaymentLink.class));
    }

    @Test
    void listMyLinksReturnsPaginatedResults() {
        PaymentLink link = PaymentLink.builder()
                .shortCode("ABC1234").title("Link 1").amount(1000L).currency("NGN")
                .status(PaymentLinkStatus.ACTIVE).currentUses(0).build();
        link.setId(UUID.randomUUID());

        Page<PaymentLink> page = new PageImpl<>(List.of(link));
        when(paymentLinkRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId(), PageRequest.of(0, 20)))
                .thenReturn(page);

        var result = service.listMyLinks(PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Link 1");
    }

    @Test
    void getMyLinkThrowsForNonexistentId() {
        UUID fakeId = UUID.randomUUID();
        when(paymentLinkRepository.findByIdAndUserId(fakeId, currentUser.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMyLink(fakeId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMyLinkSoftDeletesToDeletedStatus() {
        PaymentLink link = PaymentLink.builder()
                .shortCode("ABC1234").status(PaymentLinkStatus.ACTIVE).currentUses(0).build();
        link.setId(UUID.randomUUID());
        when(paymentLinkRepository.findByIdAndUserId(link.getId(), currentUser.getId()))
                .thenReturn(Optional.of(link));
        when(paymentLinkRepository.save(any(PaymentLink.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteMyLink(link.getId());

        assertThat(link.getStatus()).isEqualTo(PaymentLinkStatus.DELETED);
    }
}
