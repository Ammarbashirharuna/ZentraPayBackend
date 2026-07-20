package com.zentrapay.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of resolving a payout account. Shown to the seller as
 * "Is this you? [accountName]" before they save.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAccountResponse {

    /** Resolved account holder name from the provider. */
    private String accountName;

    /** Echoed back for confirmation. */
    private String accountNumber;

    private String bankCode;

    private String currency;
}
