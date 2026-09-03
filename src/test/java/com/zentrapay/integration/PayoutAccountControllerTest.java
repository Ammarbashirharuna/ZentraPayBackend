package com.zentrapay.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zentrapay.controller.PayoutAccountController;
import com.zentrapay.dto.payout.PayoutAccountResponse;
import com.zentrapay.dto.payout.SavePayoutAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountRequest;
import com.zentrapay.dto.payout.ValidateAccountResponse;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.exception.DuplicateResourceException;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.service.PayoutAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PayoutAccountController.class)
class PayoutAccountControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean PayoutAccountService payoutAccountService;
    @MockitoBean com.zentrapay.repository.ApiKeyRepository apiKeyRepository;
    @MockitoBean com.zentrapay.service.EmailService emailService;

    @Test
    void validateReturns200WithAccountName() throws Exception {
        when(payoutAccountService.validateAccount(any(ValidateAccountRequest.class)))
                .thenReturn(ValidateAccountResponse.builder()
                        .accountName("Ada Seller").accountNumber("0123456789")
                        .bankCode("058").currency("NGN").build());

        mockMvc.perform(post("/api/v1/payout-accounts/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                ValidateAccountRequest.builder()
                                        .bankCode("058").accountNumber("0123456789").currency("NGN").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").value("Ada Seller"));
    }

    @Test
    void saveReturns201WithAccount() throws Exception {
        when(payoutAccountService.savePayoutAccount(any(SavePayoutAccountRequest.class)))
                .thenReturn(PayoutAccountResponse.builder()
                        .id(UUID.randomUUID()).accountName("Ada Seller")
                        .bankName("Wema Bank").accountValidated(true).isActive(true)
                        .createdAt(LocalDateTime.now()).build());

        mockMvc.perform(post("/api/v1/payout-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                SavePayoutAccountRequest.builder()
                                        .country("NG").currency("NGN").method(PayoutMethod.BANK_ACCOUNT)
                                        .bankCode("058").accountNumber("0123456789").bankName("Wema Bank").build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountName").value("Ada Seller"));
    }

    @Test
    void saveReturns409OnDuplicate() throws Exception {
        when(payoutAccountService.savePayoutAccount(any(SavePayoutAccountRequest.class)))
                .thenThrow(new DuplicateResourceException("Already have an account."));

        mockMvc.perform(post("/api/v1/payout-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                SavePayoutAccountRequest.builder()
                                        .country("NG").currency("NGN").method(PayoutMethod.BANK_ACCOUNT)
                                        .bankCode("058").accountNumber("0123456789").bankName("Wema Bank").build())))
                .andExpect(status().isConflict());
    }

    @Test
    void getReturns200WithAccount() throws Exception {
        when(payoutAccountService.getPayoutAccount())
                .thenReturn(PayoutAccountResponse.builder()
                        .id(UUID.randomUUID()).accountName("Ada Seller").build());

        mockMvc.perform(get("/api/v1/payout-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").value("Ada Seller"));
    }

    @Test
    void getReturns404WhenNone() throws Exception {
        when(payoutAccountService.getPayoutAccount())
                .thenThrow(new ResourceNotFoundException("No account found."));

        mockMvc.perform(get("/api/v1/payout-accounts"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns200() throws Exception {
        doNothing().when(payoutAccountService).deletePayoutAccount();

        mockMvc.perform(delete("/api/v1/payout-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
