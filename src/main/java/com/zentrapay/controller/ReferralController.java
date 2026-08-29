package com.zentrapay.controller;

import com.zentrapay.dto.ApiResponse;
import com.zentrapay.dto.referral.ReferralResponse;
import com.zentrapay.service.ReferralService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/referrals")
@RequiredArgsConstructor
@Tag(name = "Referrals", description = "Referral program — share your code and earn")
public class ReferralController {

    private final ReferralService referralService;

    @GetMapping("/me")
    @Operation(summary = "Get my referral code",
            description = "Returns your unique referral code and stats, creating one if needed.")
    public ResponseEntity<ApiResponse<ReferralResponse>> getMyReferral() {
        ReferralResponse response = referralService.getMyReferral();
        return ResponseEntity.ok(ApiResponse.success(response, "Referral code retrieved"));
    }
}
