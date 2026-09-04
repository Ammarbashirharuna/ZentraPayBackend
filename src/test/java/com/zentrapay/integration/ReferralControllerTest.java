package com.zentrapay.integration;

import com.zentrapay.controller.ReferralController;
import com.zentrapay.dto.referral.ReferralResponse;
import com.zentrapay.service.ReferralService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReferralControllerTest extends AbstractControllerTest {

    @MockitoBean ReferralService referralService;

    @Test
    void getMyReferralReturns200() throws Exception {
        when(referralService.getMyReferral()).thenReturn(
                ReferralResponse.builder().referralCode("ABC12345")
                        .referralUrl("http://localhost:8080/register?ref=ABC12345")
                        .usedCount(3).totalEarnings(0L).createdAt(LocalDateTime.now()).build());
        mockMvc.perform(get("/api/v1/referrals/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralCode").value("ABC12345"))
                .andExpect(jsonPath("$.data.usedCount").value(3));
    }
}
