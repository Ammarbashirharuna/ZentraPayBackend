package com.zentrapay.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Bank information returned from Paystack's /bank endpoint.
 * Used for the bank search dropdown in the payout account setup.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankInfo {
    private String name;
    private String code;
    private String longcode;
    private String currency;
    private String type;
    private boolean active;
}
