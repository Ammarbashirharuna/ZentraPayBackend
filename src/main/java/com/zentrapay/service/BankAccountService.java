package com.zentrapay.service;

import com.zentrapay.client.PaystackClient;
import com.zentrapay.dto.bankaccount.BankAccountResponse;
import com.zentrapay.dto.bankaccount.SaveBankAccountRequest;
import com.zentrapay.dto.bankaccount.VerifyAccountRequest;
import com.zentrapay.dto.bankaccount.VerifyAccountResponse;
import com.zentrapay.entity.BankAccount;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.BankAccountRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Bank Account Service
 *
 * Handles all business logic for bank accounts:
 * - Verify accounts with Paystack
 * - Create subaccounts
 * - Save bank accounts
 * - Retrieve bank accounts
 * - Manage bank account lifecycle
 *
 * Transaction Flow:
 * User enters bank details
 *     ↓
 * VerifyBankAccount() → calls Paystack → returns account name
 *     ↓ (User confirms)
 * SaveBankAccount() → creates Paystack subaccount → saves to DB
 *     ↓
 * Bank account ready to receive payments
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankAccountService {

    /**
     * All dependencies injected by Spring
     * @RequiredArgsConstructor creates constructor automatically
     */
    private final BankAccountRepository bankAccountRepository;
    private final UserRepository userRepository;
    private final PaystackClient paystackClient;

    /**
     * Get current logged-in user
     *
     * Every endpoint requires authentication (JWT token).
     * This helper extracts the current user from the security context.
     *
     * @return The authenticated user
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new ResourceNotFoundException("User not found"));
    }

    /**
     * Verify bank account with Paystack
     *
     * Step 1 of the bank account setup flow:
     * 1. User enters account number + bank code
     * 2. We call Paystack to verify it exists
     * 3. Paystack returns the account holder's name
     * 4. We return the name to the user for confirmation
     *
     * Why separate verification step?
     * - Prevents typos (user sees their actual name)
     * - Builds trust (they confirm "Yes, that's me")
     * - Better UX (shows immediate feedback)
     *
     * @param request Account number + bank code
     * @return Account name from Paystack for user confirmation
     * @throws Exception if verification fails
     */
    public VerifyAccountResponse verifyBankAccount(
            VerifyAccountRequest request) {

        log.info("Verifying bank account for user: {}",
                getCurrentUser().getId());

        // Call Paystack to verify the account
        String accountName = paystackClient.verifyBankAccount(
                request.getBankCode(),
                request.getAccountNumber()
        );

        // Check if verification succeeded
        if (accountName == null) {
            log.warn("Bank account verification failed: {}/{}",
                    request.getBankCode(),
                    request.getAccountNumber());
            throw new RuntimeException(
                    "Account verification failed. Please check your " +
                            "account number and bank code."
            );
        }

        // Get the bank name from the code
        // (In production, you'd use a bank list service)
        String bankName = getBankName(request.getBankCode());

        log.info("Bank account verified: {}", accountName);

        // Return the verified details
        return VerifyAccountResponse.builder()
                .accountName(accountName)
                .accountNumber(request.getAccountNumber())
                .bankCode(request.getBankCode())
                .bankName(bankName)
                .build();
    }

    /**
     * Save bank account after verification
     *
     * Step 2 of the flow:
     * 1. User confirms verification details
     * 2. We create a Paystack subaccount
     * 3. We save the bank account to our database
     *
     * The subaccount is the key to this entire system.
     * When a customer pays → Paystack automatically sends to this subaccount
     * → Which is linked to the seller's bank account
     *
     * @param request Verified account details
     * @return Saved bank account with all details
     */
    @Transactional
    public BankAccountResponse saveBankAccount(
            SaveBankAccountRequest request) {

        User currentUser = getCurrentUser();
        log.info("Saving bank account for user: {}", currentUser.getId());

        // Check if user already has a bank account
        // One seller = one bank account (for now)
        if (bankAccountRepository.existsByUserId(currentUser.getId())) {
            log.warn("User already has a bank account: {}",
                    currentUser.getId());
            throw new RuntimeException(
                    "You already have a bank account set up. " +
                            "You can only have one bank account per account."
            );
        }

        // Step 1: Create Paystack subaccount
        // This is critical — without this, we can't receive payments
        String subaccountCode = paystackClient.createSubaccount(
                currentUser.getFullName(),  // business name
                request.getBankCode(),
                request.getAccountNumber()
        );

        if (subaccountCode == null) {
            log.error("Failed to create Paystack subaccount for user: {}",
                    currentUser.getId());
            throw new RuntimeException(
                    "Failed to set up payment account. " +
                            "Please try again."
            );
        }

        log.info("Paystack subaccount created: {}", subaccountCode);

        // Step 2: Create and save BankAccount entity
        BankAccount bankAccount = BankAccount.builder()
                .user(currentUser)
                .bankName(request.getBankName())
                .bankCode(request.getBankCode())
                .accountNumber(request.getAccountNumber())
                .accountName(request.getAccountName())
                .paystackSubaccountCode(subaccountCode)
                .isActive(true)
                .build();

        BankAccount savedAccount = bankAccountRepository.save(bankAccount);

        log.info("Bank account saved successfully: {}", savedAccount.getId());

        // Step 3: Return the saved account as response
        return buildBankAccountResponse(savedAccount);
    }

    /**
     * Get current user's bank account
     *
     * Used when:
     * - User views their settings
     * - User wants to see their bank account details
     * - Controller needs to check if account exists
     *
     * @return User's bank account if exists
     * @throws ResourceNotFoundException if no account found
     */
    public BankAccountResponse getBankAccount() {
        User currentUser = getCurrentUser();

        log.info("Retrieving bank account for user: {}", currentUser.getId());

        BankAccount bankAccount = bankAccountRepository
                .findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bank account found. Please set up a bank account " +
                                "to receive payments."
                ));

        return buildBankAccountResponse(bankAccount);
    }

    /**
     * Delete user's bank account
     *
     * Used when:
     * - User wants to remove their bank account
     * - User wants to add a different bank account (delete old, add new)
     *
     * Important: We don't delete the database record.
     * We just mark isActive as false.
     * This is called "soft delete" — keeps audit trail.
     *
     * @throws ResourceNotFoundException if no account found
     */
    @Transactional
    public void deleteBankAccount() {
        User currentUser = getCurrentUser();

        log.info("Deleting bank account for user: {}", currentUser.getId());

        BankAccount bankAccount = bankAccountRepository
                .findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No bank account found to delete."
                ));

        // Soft delete — mark as inactive instead of deleting
        bankAccount.setIsActive(false);
        bankAccountRepository.save(bankAccount);

        log.info("Bank account deactivated: {}", bankAccount.getId());
    }

    /**
     * Helper: Convert BankAccount entity to response DTO
     *
     * Why separate method?
     * - Used in multiple places (save, get)
     * - Keeps logic in one place
     * - Easy to add new fields to response later
     *
     * @param bankAccount The entity from database
     * @return Response DTO for API
     */
    private BankAccountResponse buildBankAccountResponse(
            BankAccount bankAccount) {

        return BankAccountResponse.builder()
                .id(bankAccount.getId())
                .bankName(bankAccount.getBankName())
                .accountNumber(bankAccount.getAccountNumber())
                .accountName(bankAccount.getAccountName())
                .isActive(bankAccount.getIsActive())
                .createdAt(bankAccount.getCreatedAt())
                .updatedAt(bankAccount.getUpdatedAt())
                .status("Account verified and active")
                .build();
    }

    /**
     * Helper: Get bank name from bank code
     *
     * Currently returns placeholder.
     * In production, you'd use a proper bank list.
     *
     * Common Nigerian banks:
     * 058 = Guarantee Trust Bank (GTBank)
     * 044 = Access Bank
     * 011 = First Bank
     * 057 = Zenith Bank
     * 033 = United Bank for Africa (UBA)
     *
     * For now, we'll return a generic message.
     * Frontend already has the bank name from Paystack.
     *
     * @param bankCode The Paystack bank code
     * @return Bank name
     */
    private String getBankName(String bankCode) {
        // In production, query a banks table or API
        // For now, return the code (frontend has the name)
        return "Bank-" + bankCode;
    }
}