package com.zentrapay.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
@Slf4j
public class EmailService {

    private final Resend resend;

    @Value("${resend.from-email}")
    private String fromEmail;

    @Value("${app.base-url}")
    private String baseUrl;

    public EmailService(@Value("${resend.api-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    public void sendVerificationEmail(String toEmail, String fullName, String token) {
        String verificationUrl = baseUrl + "/api/v1/auth/verify?token=" + token;
        log.info("Sending verification email to: {}", toEmail);
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(toEmail)
                    .subject("Verify your ZentraPay email address")
                    .html(buildVerificationHtml(fullName, verificationUrl))
                    .build();
            resend.emails().send(email);
            log.info("Verification email sent to: {}", toEmail);
        } catch (ResendException e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage());
        }
    }

    public void sendPaymentReceivedEmail(User seller, Payment payment, PaymentLink link) {
        log.info("Sending payment received email to seller: {}", seller.getEmail());
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(seller.getEmail())
                    .subject("Payment received - " + formatAmount(payment.getAmount(), payment.getCurrency()))
                    .html(buildPaymentReceivedHtml(seller.getFullName(), payment, link))
                    .build();
            resend.emails().send(email);
        } catch (ResendException e) {
            log.error("Failed to send payment received email: {}", e.getMessage());
        }
    }

    public void sendPaymentReceiptEmail(String customerEmail, Payment payment, PaymentLink link) {
        log.info("Sending payment receipt email to: {}", customerEmail);
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(customerEmail)
                    .subject("Payment confirmation - " + link.getTitle())
                    .html(buildPaymentReceiptHtml(payment, link))
                    .build();
            resend.emails().send(email);
        } catch (ResendException e) {
            log.error("Failed to send payment receipt email: {}", e.getMessage());
        }
    }

    public void sendLinkExpiringReminder(String customerEmail, PaymentLink link) {
        log.info("Sending link expiring reminder to: {}", customerEmail);
        try {
            String payUrl = baseUrl + "/api/v1/pay/" + link.getShortCode();
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(customerEmail)
                    .subject("Your payment link is expiring soon - " + link.getTitle())
                    .html(buildLinkExpiringHtml(link, payUrl))
                    .build();
            resend.emails().send(email);
        } catch (ResendException e) {
            log.error("Failed to send link expiring reminder: {}", e.getMessage());
        }
    }

    public void sendAbandonedCheckoutReminder(String customerEmail, PaymentLink link) {
        log.info("Sending abandoned checkout reminder to: {}", customerEmail);
        try {
            String payUrl = baseUrl + "/api/v1/pay/" + link.getShortCode();
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(customerEmail)
                    .subject("Incomplete - finish paying for " + link.getTitle())
                    .html(buildAbandonedCheckoutHtml(link, payUrl))
                    .build();
            resend.emails().send(email);
        } catch (ResendException e) {
            log.error("Failed to send abandoned checkout reminder: {}", e.getMessage());
        }
    }

    private String buildVerificationHtml(String fullName, String url) {
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:#1a1a1a;">Welcome to ZentraPay, %s!</h2>
                <p style="color:#444;font-size:16px;">Please verify your email to activate your account.</p>
                <a href="%s" style="display:inline-block;padding:14px 28px;background:#0070f3;color:#fff;text-decoration:none;border-radius:6px;font-weight:bold;font-size:16px;margin:20px 0;">Verify Email</a>
                <p style="color:#666;font-size:14px;">This link expires in 24 hours.</p>
                <hr style="border:none;border-top:1px solid #eee;margin:30px 0;">
                <p style="color:#999;font-size:12px;">ZentraPay - Your central hub for payment collection</p>
                </body></html>
                """.formatted(fullName, url);
    }

    private String buildPaymentReceivedHtml(String sellerName, Payment payment, PaymentLink link) {
        String amt = formatAmount(payment.getAmount(), payment.getCurrency());
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:#1a1a1a;">You got paid!</h2>
                <p style="color:#444;font-size:16px;">Hi %s,</p>
                <p style="color:#444;font-size:16px;">A customer paid <strong>%s</strong> for <strong>%s</strong>.</p>
                <div style="background:#f7f7f7;border-radius:8px;padding:20px;margin:20px 0;">
                <table style="width:100%%;font-size:14px;">
                <tr><td style="color:#666;padding:4px 0;">Amount</td><td style="font-weight:bold;">%s</td></tr>
                <tr><td style="color:#666;padding:4px 0;">Reference</td><td>%s</td></tr>
                <tr><td style="color:#666;padding:4px 0;">Customer</td><td>%s</td></tr>
                </table></div>
                <p style="color:#444;font-size:14px;">Funds are being settled to your payout account. Track in your dashboard.</p>
                <hr style="border:none;border-top:1px solid #eee;margin:30px 0;">
                <p style="color:#999;font-size:12px;">ZentraPay - Your central hub for payment collection</p>
                </body></html>
                """.formatted(sellerName, amt, link.getTitle(), amt, payment.getProviderReference(), payment.getCustomerEmail());
    }

    private String buildPaymentReceiptHtml(Payment payment, PaymentLink link) {
        String amt = formatAmount(payment.getAmount(), payment.getCurrency());
        String date = payment.getPaidAt() != null
                ? payment.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:#1a1a1a;">Payment Confirmed</h2>
                <p style="color:#444;font-size:16px;">Your payment of <strong>%s</strong> for <strong>%s</strong> has been confirmed.</p>
                <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:8px;padding:20px;margin:20px 0;">
                <table style="width:100%%;font-size:14px;">
                <tr><td style="color:#666;padding:4px 0;">Amount</td><td style="font-weight:bold;">%s</td></tr>
                <tr><td style="color:#666;padding:4px 0;">Date</td><td>%s</td></tr>
                <tr><td style="color:#666;padding:4px 0;">Reference</td><td>%s</td></tr>
                </table></div>
                <p style="color:#666;font-size:14px;">Save this email as your payment receipt.</p>
                <hr style="border:none;border-top:1px solid #eee;margin:30px 0;">
                <p style="color:#999;font-size:12px;">ZentraPay - Your central hub for payment collection</p>
                </body></html>
                """.formatted(amt, link.getTitle(), amt, date, payment.getProviderReference());
    }

    private String buildLinkExpiringHtml(PaymentLink link, String payUrl) {
        String amt = formatAmount(link.getAmount(), link.getCurrency());
        String expiry = link.getExpiresAt() != null
                ? link.getExpiresAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "soon";
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:#1a1a1a;">Your payment link is expiring</h2>
                <p style="color:#444;font-size:16px;">The link for <strong>%s</strong> (%s) expires on <strong>%s</strong>.</p>
                <a href="%s" style="display:inline-block;padding:14px 28px;background:#0070f3;color:#fff;text-decoration:none;border-radius:6px;font-weight:bold;font-size:16px;margin:20px 0;">Complete Payment</a>
                <hr style="border:none;border-top:1px solid #eee;margin:30px 0;">
                <p style="color:#999;font-size:12px;">ZentraPay - Your central hub for payment collection</p>
                </body></html>
                """.formatted(link.getTitle(), amt, expiry, payUrl);
    }

    private String buildAbandonedCheckoutHtml(PaymentLink link, String payUrl) {
        String amt = formatAmount(link.getAmount(), link.getCurrency());
        return """
                <!DOCTYPE html><html><body style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;">
                <h2 style="color:#1a1a1a;">Incomplete payment</h2>
                <p style="color:#444;font-size:16px;">You started paying for <strong>%s</strong> (%s) but did not finish.</p>
                <a href="%s" style="display:inline-block;padding:14px 28px;background:#0070f3;color:#fff;text-decoration:none;border-radius:6px;font-weight:bold;font-size:16px;margin:20px 0;">Complete Payment</a>
                <hr style="border:none;border-top:1px solid #eee;margin:30px 0;">
                <p style="color:#999;font-size:12px;">ZentraPay - Your central hub for payment collection</p>
                </body></html>
                """.formatted(link.getTitle(), amt, payUrl);
    }

    private String formatAmount(long minorUnits, String currency) {
        String sym = switch (currency.toUpperCase()) {
            case "NGN" -> "NGN ";
            case "GHS" -> "GHS ";
            case "KES" -> "KES ";
            case "ZAR" -> "ZAR ";
            case "USD" -> "USD ";
            default -> currency + " ";
        };
        NumberFormat fmt = NumberFormat.getNumberInstance(Locale.US);
        fmt.setMinimumFractionDigits(2);
        fmt.setMaximumFractionDigits(2);
        return sym + fmt.format(minorUnits / 100.0);
    }
}
