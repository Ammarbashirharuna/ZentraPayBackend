package com.zentrapay.integration;

import com.zentrapay.controller.PayoutController;
import com.zentrapay.dto.payout.PayoutResponse;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.PayoutQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PayoutControllerTest extends AbstractControllerTest {

    @MockitoBean PayoutQueryService payoutQueryService;

    @Test
    void listReturns200() throws Exception {
        when(payoutQueryService.listMyPayouts(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(
                        PayoutResponse.builder().id(UUID.randomUUID()).reference("PO-ZP-1")
                                .amount(9_900L).currency("NGN").status("PAID")
                                .completedAt(LocalDateTime.now()).build()
                ), PageRequest.of(0, 20), 1));
        mockMvc.perform(get("/api/v1/payouts").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reference").value("PO-ZP-1"));
    }

    @Test
    void getReturns404WhenNotFound() throws Exception {
        when(payoutQueryService.getMyPayout(any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Not found"));
        mockMvc.perform(get("/api/v1/payouts/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
