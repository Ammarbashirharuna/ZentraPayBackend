package com.zentrapay.service;

import com.zentrapay.dto.payout.SavePayoutAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountRequest;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.entity.User;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.provider.AccountValidationRequest;
import com.zentrapay.provider.AccountValidationResult;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.repository.PayoutAccountRepository;
import com.zentrapay.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayoutAccountServiceTest {

    @Mock PayoutAccountRepository payoutAccountRepository;
    @Mock UserRepository userRepository;
    @Mock PaymentProvider paymentProvider;

    @InjectMocks PayoutAccountService service;

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

    // ── Validate ────────────────────────────────────────────────────────────

    @Test
    void validateAccountResolvesNameOnSuccess() {
        when(paymentProvider.validateAccount(any(AccountValidationRequest.class)))
                .thenReturn(AccountValidationResult.builder().valid(true).accountName("Ada Seller").build());

        var result = service.validateAccount(ValidateAccountRequest.builder()
                .bankCode("058").accountNumber("0123456789").currency("NGN").build());

        assertThat(result.getAccountName()).isEqualTo("Ada Seller");
        assertThat(result.getAccountNumber()).isEqualTo("0123456789");
    }

    @Test
    void validateAccountThrowsOnInvalidAccount() {
        when(paymentProvider.validateAccount(any(AccountValidationRequest.class)))
                .thenReturn(AccountValidationResult.builder().valid(false).accountName(null).build());

        assertThatThrownBy(() -> service.validateAccount(ValidateAccountRequest.builder()
                .bankCode("000").accountNumber("0000000000").currency("NGN").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("validation failed");
    }

    // ── Save ────────────────────────────────────────────────────────────────

    @Test
    void savePayoutAccountSucceedsOnFirstAccount() {
        when(payoutAccountRepository.existsByUserId(currentUser.getId())).thenReturn(false);
        when(paymentProvider.validateAccount(any(AccountValidationRequest.class)))
                .thenReturn(AccountValidationResult.builder().valid(true).accountName("Ada Seller").build());
        when(payoutAccountRepository.save(any(PayoutAccount.class))).thenAnswer(inv -> {
            PayoutAccount a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        var response = service.savePayoutAccount(SavePayoutAccountRequest.builder()
                .country("NG").currency("NGN").method(PayoutMethod.BANK_ACCOUNT)
                .bankCode("058").accountNumber("0123456789").bankName("Wema Bank").build());

        assertThat(response.getAccountName()).isEqualTo("Ada Seller");
        assertThat(response.getAccountValidated()).isTrue();
        assertThat(response.getIsActive()).isTrue();
    }

    @Test
    void savePayoutAccountRejectsDuplicate() {
        when(payoutAccountRepository.existsByUserId(currentUser.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.savePayoutAccount(SavePayoutAccountRequest.builder()
                .country("NG").currency("NGN").method(PayoutMethod.BANK_ACCOUNT)
                .bankCode("058").accountNumber("0123456789").bankName("Wema Bank").build()))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void savePayoutAccountRevalidatesWithProvider() {
        when(payoutAccountRepository.existsByUserId(currentUser.getId())).thenReturn(false);
        // First call: validate endpoint. Second call: re-validate on save.
        when(paymentProvider.validateAccount(any(AccountValidationRequest.class)))
                .thenReturn(AccountValidationResult.builder().valid(true).accountName("Ada Seller").build());
        when(payoutAccountRepository.save(any(PayoutAccount.class))).thenAnswer(inv -> {
            PayoutAccount a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });

        service.savePayoutAccount(SavePayoutAccountRequest.builder()
                .country("NG").currency("NGN").method(PayoutMethod.BANK_ACCOUNT)
                .bankCode("058").accountNumber("0123456789").bankName("Wema Bank").build());

        // Called twice: once from the service's re-validation
        verify(paymentProvider, times(1)).validateAccount(any(AccountValidationRequest.class));
    }

    // ── Get ─────────────────────────────────────────────────────────────────

    @Test
    void getPayoutAccountReturnsExisting() {
        PayoutAccount account = PayoutAccount.builder()
                .id(UUID.randomUUID()).user(currentUser).country("NG").currency("NGN")
                .method(PayoutMethod.BANK_ACCOUNT).bankName("Wema Bank").bankCode("058")
                .accountNumber("0123456789").accountName("Ada Seller")
                .accountValidated(true).isActive(true).build();

        when(payoutAccountRepository.findByUserId(currentUser.getId())).thenReturn(Optional.of(account));

        var response = service.getPayoutAccount();

        assertThat(response.getAccountName()).isEqualTo("Ada Seller");
        assertThat(response.getBankName()).isEqualTo("Wema Bank");
    }

    @Test
    void getPayoutAccountThrowsWhenNoneExists() {
        when(payoutAccountRepository.findByUserId(currentUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayoutAccount())
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Delete ──────────────────────────────────────────────────────────────

    @Test
    void deletePayoutAccountSoftDeletes() {
        PayoutAccount account = PayoutAccount.builder()
                .id(UUID.randomUUID()).user(currentUser).isActive(true).build();

        when(payoutAccountRepository.findByUserId(currentUser.getId())).thenReturn(Optional.of(account));
        when(payoutAccountRepository.save(any(PayoutAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deletePayoutAccount();

        assertThat(account.getIsActive()).isFalse();
    }

    @Test
    void deletePayoutAccountThrowsWhenNoneExists() {
        when(payoutAccountRepository.findByUserId(currentUser.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePayoutAccount())
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
