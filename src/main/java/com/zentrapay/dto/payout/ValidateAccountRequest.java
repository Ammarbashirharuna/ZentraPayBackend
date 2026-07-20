package com.zentrapay.dto.payout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step 1 of payout-account onboarding: ask the provider to resolve the account
 * holder's name so the seller can confirm before saving.
 *
 * Currency-driven: the currency tells the provider which rail/bank list to use,
 * which is what makes this work across African markets rather than NG only.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAccountRequest {

    /** Provider bank/institution code for the given currency (see GET /bank_list/{currency}). */
    @NotBlank(message = "Bank code is required")
    private String bankCode;

    /** Account number or mobile-money number. Length varies by country. */
    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
    private String accountNumber;

    /** ISO-4217 currency code (e.g. NGN, GHS, KES, ZAR). */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
}
