package com.zentrapay.service;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Periodically checks for payment links that are about to expire or have
 * abandoned checkouts, and sends reminder emails to the customers.
 *
 * Runs every hour. Only processes PENDING payments (customers who started
 * but did not complete checkout).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReminderJob {

    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentRepository paymentRepository;
    private final EmailService emailService;

    @Value("${reminder.hours-before-expiry:24}")
    private int hoursBeforeExpiry;

    @Value("${reminder.abandoned-hours:2}")
    private int abandonedHours;

    @Scheduled(fixedDelayString = "${reminder.interval-ms:3600000}",
            initialDelayString = "${reminder.initial-delay-ms:300000}")
    public void sendReminders() {
        sendExpiryReminders();
        sendAbandonedCheckoutReminders();
    }

    private void sendExpiryReminders() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusHours(hoursBeforeExpiry);

        List<PaymentLink> expiringLinks = paymentLinkRepository
                .findActiveExpiringBetween(now, deadline);

        if (expiringLinks.isEmpty()) {
            return;
        }

        log.info("Payment reminder job: {} links expiring within {} hours", expiringLinks.size(), hoursBeforeExpiry);

        for (PaymentLink link : expiringLinks) {
            try {
                List<Payment> pendingPayments = paymentRepository
                        .findByPaymentLinkIdAndStatusOrderByCreatedAtDesc(
                                link.getId(), com.zentrapay.entity.PaymentStatus.PENDING,
                                PageRequest.of(0, 50))
                        .getContent();

                for (Payment payment : pendingPayments) {
                    if (payment.getCustomerEmail() != null && !payment.getCustomerEmail().isBlank()) {
                        emailService.sendLinkExpiringReminder(payment.getCustomerEmail(), link);
                    }
                }
            } catch (Exception ex) {
                log.error("Failed to send expiry reminders for link {}: {}", link.getShortCode(), ex.getMessage());
            }
        }
    }

    private void sendAbandonedCheckoutReminders() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(abandonedHours);

        List<Payment> abandonedPayments = paymentRepository
                .findExpiringPendingPayments(cutoff.minusHours(1), cutoff);

        if (abandonedPayments.isEmpty()) {
            return;
        }

        log.info("Payment reminder job: {} abandoned checkouts to remind", abandonedPayments.size());

        for (Payment payment : abandonedPayments) {
            try {
                if (payment.getCustomerEmail() != null && !payment.getCustomerEmail().isBlank()) {
                    emailService.sendAbandonedCheckoutReminder(
                            payment.getCustomerEmail(), payment.getPaymentLink());
                }
            } catch (Exception ex) {
                log.error("Failed to send abandoned reminder for payment {}: {}",
                        payment.getProviderReference(), ex.getMessage());
            }
        }
    }
}
