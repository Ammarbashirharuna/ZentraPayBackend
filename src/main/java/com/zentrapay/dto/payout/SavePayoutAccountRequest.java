package com.zentrapay.dto.payout;

import com.zentrapay.entity.PayoutMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Step 2 of onboarding: save the payout account after the seller has confirmed
 * the resolved name. The service re-validates with the provider before saving —
 * the client is never trusted to assert the account name on its own.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavePayoutAccountRequest {

    /** ISO-3166 alpha-2 country code (e.g. NG, GH, KE, ZA). */
    @NotBlank(message = "Country is required")
    @Pattern(regexp = "^[A-Z]{2}$", message = "Country must be a 2-letter ISO code")
    private String country;

    /** ISO-4217 currency code. */
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;

    /** Payout rail. */
    @NotNull(message = "Payout method is required")
    private PayoutMethod method;

    @NotBlank(message = "Bank code is required")
    private String bankCode;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
    private String accountNumber;

    /** Human-readable bank/institution name for display. */
    @NotBlank(message = "Bank name is required")
    private String bankName;
}
