package com.zentrapay.dto.referral;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional referral code applied during registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyReferralRequest {

    @NotBlank(message = "Referral code is required")
    @Pattern(regexp = "^[A-Z0-9]{6,12}$", message = "Invalid referral code format")
    private String referralCode;
}
