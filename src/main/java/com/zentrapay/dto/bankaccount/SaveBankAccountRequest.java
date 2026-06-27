package com.zentrapay.dto.bankaccount;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to save bank account after verification
 *
 * This is Step 2 of the flow.
 *
 * Flow recap:
 * 1. User sends account number + bank code
 * 2. We verify with Paystack, get name
 * 3. User confirms "Yes, this is my account"
 * 4. User sends THIS request to actually save it
 * 5. We create Paystack subaccount
 * 6. We save to database
 *
 * Everything has been verified at this point.
 * No need to re-verify.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveBankAccountRequest {

    /**
     * Bank code (already verified in Step 1)
     */
    @NotBlank(message = "Bank code is required")
    private String bankCode;

    /**
     * Account number (already verified in Step 1)
     */
    @NotBlank(message = "Account number is required")
    @Pattern(
            regexp = "^[0-9]{10}$",
            message = "Account number must be exactly 10 digits"
    )
    private String accountNumber;

    /**
     * Account name (returned from Paystack verification)
     * We store this for records/display
     */
    @NotBlank(message = "Account name is required")
    private String accountName;

    /**
     * Bank name (user-friendly name, for display)
     * Example: "Guarantee Trust Bank"
     */
    @NotBlank(message = "Bank name is required")
    private String bankName;
}