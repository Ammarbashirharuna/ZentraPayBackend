package com.zentrapay.dto.payout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Payout account as returned to the seller. The provider recipient code is
 * intentionally omitted — internal detail.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutAccountResponse {

    private UUID id;
    private String country;
    private String currency;
    private String method;
    private String bankName;
    private String accountNumber;
    private String accountName;
    private Boolean accountValidated;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
}
