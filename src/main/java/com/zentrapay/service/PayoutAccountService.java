package com.zentrapay.service;

import com.zentrapay.dto.payout.PayoutAccountResponse;
import com.zentrapay.dto.payout.SavePayoutAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountResponse;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.User;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.provider.AccountValidationRequest;
import com.zentrapay.provider.AccountValidationResult;
import com.zentrapay.provider.PaymentProvider;
import com.zentrapay.repository.PayoutAccountRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for seller payout accounts (pan-African).
 *
 * Onboarding flow:
 * 1. {@link #validateAccount} — provider resolves the account holder name.
 * 2. Seller confirms the name.
 * 3. {@link #savePayoutAccount} — we re-validate with the provider (never trust
 *    the client's asserted name) and persist.
 *
 * Unlike the old Paystack flow, there is no subaccount to create up front:
 * CashOnRails pays out by account details at settlement time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutAccountService {

    private final PayoutAccountRepository payoutAccountRepository;
    private final UserRepository userRepository;
    private final PaymentProvider paymentProvider;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /**
     * Step 1: resolve the account holder name via the provider so the seller
     * can confirm before saving.
     */
    public ValidateAccountResponse validateAccount(ValidateAccountRequest request) {
        log.info("Validating payout account for user {} in {}",
                getCurrentUser().getId(), request.getCurrency());

        AccountValidationResult result = paymentProvider.validateAccount(
                AccountValidationRequest.builder()
                        .accountNumber(request.getAccountNumber())
                        .bankCode(request.getBankCode())
                        .currency(request.getCurrency())
                        .build());

        if (!result.valid()) {
            throw new IllegalArgumentException(
                    "Account validation failed. Check the account number, bank code and currency.");
        }

        return ValidateAccountResponse.builder()
                .accountName(result.accountName())
                .accountNumber(request.getAccountNumber())
                .bankCode(request.getBankCode())
                .currency(request.getCurrency())
                .build();
    }

    /**
     * Step 2: persist the payout account after re-validating with the provider.
     */
    @Transactional
    public PayoutAccountResponse savePayoutAccount(SavePayoutAccountRequest request) {
        User currentUser = getCurrentUser();
        log.info("Saving payout account for user {}", currentUser.getId());

        if (payoutAccountRepository.existsByUserId(currentUser.getId())) {
            throw new DuplicateResourceException(
                    "You already have a payout account. Delete it before adding a new one.");
        }

        // Re-validate server-side: the resolved name is authoritative, not the client's.
        AccountValidationResult result = paymentProvider.validateAccount(
                AccountValidationRequest.builder()
                        .accountNumber(request.getAccountNumber())
                        .bankCode(request.getBankCode())
                        .currency(request.getCurrency())
                        .build());

        if (!result.valid()) {
            throw new IllegalArgumentException(
                    "Account could not be validated with the payment provider.");
        }

        PayoutAccount account = PayoutAccount.builder()
                .user(currentUser)
                .country(request.getCountry())
                .currency(request.getCurrency())
                .method(request.getMethod())
                .bankName(request.getBankName())
                .bankCode(request.getBankCode())
                .accountNumber(request.getAccountNumber())
                .accountName(result.accountName())
                .accountValidated(true)
                .isActive(true)
                .build();

        PayoutAccount saved = payoutAccountRepository.save(account);
        log.info("Payout account saved: {}", saved.getId());
        return toResponse(saved);
    }

    public PayoutAccountResponse getPayoutAccount() {
        User currentUser = getCurrentUser();
        PayoutAccount account = payoutAccountRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No payout account found. Set one up to receive payments."));
        return toResponse(account);
    }

    @Transactional
    public void deletePayoutAccount() {
        User currentUser = getCurrentUser();
        PayoutAccount account = payoutAccountRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No payout account found to delete."));
        // Soft delete: keep the record for audit/compliance.
        account.setIsActive(false);
        payoutAccountRepository.save(account);
        log.info("Payout account deactivated: {}", account.getId());
    }

    private PayoutAccountResponse toResponse(PayoutAccount account) {
        return PayoutAccountResponse.builder()
                .id(account.getId())
                .country(account.getCountry())
                .currency(account.getCurrency())
                .method(account.getMethod() != null ? account.getMethod().name() : null)
                .bankName(account.getBankName())
                .accountNumber(account.getAccountNumber())
                .accountName(account.getAccountName())
                .accountValidated(account.getAccountValidated())
                .isActive(account.getIsActive())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .status(Boolean.TRUE.equals(account.getIsActive())
                        ? "Account validated and active"
                        : "Account inactive")
                .build();
    }
}
