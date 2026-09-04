package com.zentrapay.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.zentrapay.repository.ApiKeyRepository;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import com.zentrapay.service.EmailService;
import com.zentrapay.service.PaymentReminderJob;
import com.zentrapay.service.PayoutService;
import com.zentrapay.util.JwtUtil;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Base for controller slice tests. Uses @SpringBootTest with filter
 * disabling so the full context loads (including our @Component beans)
 * but no security/cors/rate-limit filters hit MockMvc.
 *
 * All filter dependencies are mocked here so individual test classes
 * only need to mock their own service.
 */
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public abstract class AbstractControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    // Security filter dependencies
    @MockitoBean protected JwtUtil jwtUtil;
    @MockitoBean protected PasswordEncoder passwordEncoder;
    @MockitoBean protected ApiKeyRepository apiKeyRepository;

    // PaymentReminderJob dependencies
    @MockitoBean protected PaymentLinkRepository paymentLinkRepository;
    @MockitoBean protected PaymentRepository paymentRepository;
    @MockitoBean protected EmailService emailService;
    @MockitoBean protected PayoutService payoutService;
    @MockitoBean protected PaymentReminderJob paymentReminderJob;
}
