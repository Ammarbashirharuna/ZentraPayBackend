package com.zentrapay.dto.referral;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A seller's referral code and stats, shown on the referrals page.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralResponse {

    /** The unique referral code to share. */
    private String referralCode;

    /** Full referral URL for easy sharing. */
    private String referralUrl;

    /** How many sellers signed up using this code. */
    private Integer usedCount;

    /** Cumulative earnings from the referral program (minor units). */
    private Long totalEarnings;

    private LocalDateTime createdAt;
}
