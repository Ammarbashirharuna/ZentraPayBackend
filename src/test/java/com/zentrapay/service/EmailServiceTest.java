package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks EmailService emailService;

    @Test
    void formatAmountWithNGNCurrency() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@test.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080");

        // Test the private formatAmount method via reflection
        try {
            var method = EmailService.class.getDeclaredMethod("formatAmount", long.class, String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(emailService, 1000000L, "NGN");
            assertThat(result).contains("NGN");
            assertThat(result).contains("10,000.00");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void formatAmountWithUSDCurrency() {
        ReflectionTestUtils.setField(emailService, "fromEmail", "test@test.com");
        ReflectionTestUtils.setField(emailService, "baseUrl", "http://localhost:8080");

        try {
            var method = EmailService.class.getDeclaredMethod("formatAmount", long.class, String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(emailService, 5050L, "USD");
            assertThat(result).contains("USD");
            assertThat(result).contains("50.50");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
