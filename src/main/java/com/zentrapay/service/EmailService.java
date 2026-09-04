package com.zentrapay.service;

import com.resend.Resend;
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
        log.info("Verification URL: {}", verificationUrl);
        try {
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail).to(toEmail)
                    .subject("Verify your Zetapay email address")
                    .html(buildVerificationHtml(fullName, verificationUrl))
                    .build();
            resend.emails().send(email);
            log.info("Verification email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}: {}", toEmail, e.getMessage(), e);
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
        } catch (Exception e) {
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
        } catch (Exception e) {
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            log.error("Failed to send abandoned checkout reminder: {}", e.getMessage());
        }
    }

    private String buildVerificationHtml(String fullName, String url) {
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f9fafb;">
                <div style="max-width:560px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="text-align:center;margin-bottom:32px;">
                    <div style="display:inline-block;width:40px;height:40px;border-radius:10px;background:#4f46e5;line-height:40px;font-size:18px;color:white;font-weight:bold;">Z</div>
                  </div>
                  <div style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                    <div style="background:#4f46e5;padding:32px 32px 28px;text-align:center;">
                      <h1 style="color:white;font-size:22px;font-weight:700;margin:0 0 6px;letter-spacing:-0.5px;">Welcome to Zetapay</h1>
                      <p style="color:rgba(255,255,255,0.7);font-size:13px;margin:0;">Verify your email to get started</p>
                    </div>
                    <div style="padding:32px;">
                      <p style="color:#374151;font-size:15px;margin:0 0 16px;">Hi %s,</p>
                      <p style="color:#6b7280;font-size:14px;margin:0 0 24px;line-height:1.6;">Thanks for signing up. Click the button below to verify your email address and activate your account.</p>
                      <div style="text-align:center;margin:0 0 24px;">
                        <a href="%s" style="display:inline-block;padding:14px 32px;background:#4f46e5;color:#fff;text-decoration:none;border-radius:10px;font-weight:600;font-size:15px;">Verify Email</a>
                      </div>
                      <p style="color:#9ca3af;font-size:13px;margin:0;text-align:center;">This link expires in 24 hours.</p>
                    </div>
                    <div style="border-top:1px solid #e5e7eb;padding:20px 32px;text-align:center;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">Secured by <strong style="color:#6b7280;">Zetapay</strong> — zetapay.com</p>
                    </div>
                  </div>
                </div>
                </body></html>
                """.formatted(fullName, url);
    }

    private String buildPaymentReceivedHtml(String sellerName, Payment payment, PaymentLink link) {
        String amt = formatAmount(payment.getAmount(), payment.getCurrency());
        String date = payment.getPaidAt() != null
                ? payment.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f9fafb;">
                <div style="max-width:560px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <div style="text-align:center;margin-bottom:32px;">
                  <div style="display:inline-block;width:40px;height:40px;border-radius:10px;background:#4f46e5;line-height:40px;font-size:18px;color:white;font-weight:bold;">Z</div>
                </div>
                <div style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                  <div style="background:#4f46e5;padding:32px;text-align:center;">
                    <h1 style="color:white;font-size:22px;font-weight:700;margin:0 0 6px;letter-spacing:-0.5px;">You got paid! 🎉</h1>
                    <p style="color:rgba(255,255,255,0.7);font-size:13px;margin:0;">Payment received successfully</p>
                  </div>
                  <div style="padding:32px;">
                    <p style="color:#374151;font-size:15px;margin:0 0 16px;">Hi %s,</p>
                    <p style="color:#6b7280;font-size:14px;margin:0 0 24px;">A customer paid <strong style="color:#111827;">%s</strong> for <strong style="color:#111827;">%s</strong>.</p>
                    <div style="background:#f9fafb;border:1px solid #e5e7eb;border-radius:12px;padding:20px;margin:0 0 24px;">
                      <table style="width:100%%;border-collapse:collapse;">
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;border-bottom:1px solid #e5e7eb;">Amount</td><td style="color:#111827;font-weight:600;font-size:14px;text-align:right;padding:8px 0;border-bottom:1px solid #e5e7eb;">%s</td></tr>
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;border-bottom:1px solid #e5e7eb;">Reference</td><td style="color:#111827;font-size:13px;font-family:monospace;text-align:right;padding:8px 0;border-bottom:1px solid #e5e7eb;">%s</td></tr>
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;border-bottom:1px solid #e5e7eb;">Customer</td><td style="color:#111827;font-size:13px;text-align:right;padding:8px 0;border-bottom:1px solid #e5e7eb;">%s</td></tr>
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;">Date</td><td style="color:#111827;font-size:13px;text-align:right;padding:8px 0;">%s</td></tr>
                      </table>
                    </div>
                    <p style="color:#6b7280;font-size:13px;margin:0 0 24px;">Funds are being settled to your payout account. Track progress in your <a href="https://zetapay.com/payouts" style="color:#4f46e5;text-decoration:none;font-weight:500;">dashboard</a>.</p>
                  </div>
                  <div style="border-top:1px solid #e5e7eb;padding:20px 32px;text-align:center;">
                    <p style="color:#9ca3af;font-size:11px;margin:0;">Secured by <strong style="color:#6b7280;">Zetapay</strong> — zetapay.com</p>
                  </div>
                </div>
                </div>
                </body></html>
                """.formatted(sellerName, amt, link.getTitle(), amt, payment.getProviderReference(), payment.getCustomerEmail(), date);
    }

    private String buildPaymentReceiptHtml(Payment payment, PaymentLink link) {
        String amt = formatAmount(payment.getAmount(), payment.getCurrency());
        String date = payment.getPaidAt() != null
                ? payment.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))
                : LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"));
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f9fafb;">
                <div style="max-width:560px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <div style="text-align:center;margin-bottom:32px;">
                  <div style="display:inline-block;width:40px;height:40px;border-radius:10px;background:#4f46e5;line-height:40px;font-size:18px;color:white;font-weight:bold;">Z</div>
                </div>
                <div style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                  <div style="background:#4f46e5;padding:32px;text-align:center;">
                    <div style="width:56px;height:56px;border-radius:50%%;background:rgba(255,255,255,0.2);display:inline-flex;align-items:center;justify-content:center;font-size:28px;margin-bottom:12px;">✓</div>
                    <h1 style="color:white;font-size:22px;font-weight:700;margin:0 0 6px;letter-spacing:-0.5px;">Payment Confirmed</h1>
                    <p style="color:rgba(255,255,255,0.7);font-size:13px;margin:0;">Your payment has been processed</p>
                  </div>
                  <div style="padding:32px;">
                    <div style="text-align:center;margin:0 0 24px;">
                      <p style="color:#111827;font-size:28px;font-weight:700;margin:0;">%s</p>
                      <p style="color:#6b7280;font-size:14px;margin:4px 0 0;">for %s</p>
                    </div>
                    <div style="background:#f0fdf4;border:1px solid #bbf7d0;border-radius:12px;padding:20px;margin:0 0 24px;">
                      <table style="width:100%%;border-collapse:collapse;">
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;border-bottom:1px solid #d1fae5;">Amount</td><td style="color:#111827;font-weight:600;font-size:14px;text-align:right;padding:8px 0;border-bottom:1px solid #d1fae5;">%s</td></tr>
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;border-bottom:1px solid #d1fae5;">Date</td><td style="color:#111827;font-size:13px;text-align:right;padding:8px 0;border-bottom:1px solid #d1fae5;">%s</td></tr>
                        <tr><td style="color:#6b7280;font-size:13px;padding:8px 0;">Reference</td><td style="color:#111827;font-size:13px;font-family:monospace;text-align:right;padding:8px 0;">%s</td></tr>
                      </table>
                    </div>
                    <p style="color:#6b7280;font-size:13px;margin:0;text-align:center;">This email serves as your official payment receipt.</p>
                  </div>
                  <div style="border-top:1px solid #e5e7eb;padding:20px 32px;text-align:center;">
                    <p style="color:#9ca3af;font-size:11px;margin:0;">Secured by <strong style="color:#6b7280;">Zetapay</strong> — zetapay.com</p>
                  </div>
                </div>
                </div>
                </body></html>
                """.formatted(amt, link.getTitle(), amt, date, payment.getProviderReference());
    }

    private String buildLinkExpiringHtml(PaymentLink link, String payUrl) {
        String amt = formatAmount(link.getAmount(), link.getCurrency());
        String expiry = link.getExpiresAt() != null
                ? link.getExpiresAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")) : "soon";
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f9fafb;">
                <div style="max-width:560px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="text-align:center;margin-bottom:32px;">
                    <div style="display:inline-block;width:40px;height:40px;border-radius:10px;background:#4f46e5;line-height:40px;font-size:18px;color:white;font-weight:bold;">Z</div>
                  </div>
                  <div style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                    <div style="background:#f59e0b;padding:32px 32px 28px;text-align:center;">
                      <h1 style="color:white;font-size:22px;font-weight:700;margin:0 0 6px;letter-spacing:-0.5px;">Link Expiring Soon</h1>
                      <p style="color:rgba(255,255,255,0.7);font-size:13px;margin:0;">Complete your payment before it expires</p>
                    </div>
                    <div style="padding:32px;text-align:center;">
                      <p style="color:#374151;font-size:15px;margin:0 0 8px;">The link for <strong style="color:#111827;">%s</strong> (%s) expires on <strong style="color:#111827;">%s</strong>.</p>
                      <div style="margin:24px 0;">
                        <a href="%s" style="display:inline-block;padding:14px 32px;background:#4f46e5;color:#fff;text-decoration:none;border-radius:10px;font-weight:600;font-size:15px;">Complete Payment</a>
                      </div>
                    </div>
                    <div style="border-top:1px solid #e5e7eb;padding:20px 32px;text-align:center;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">Secured by <strong style="color:#6b7280;">Zetapay</strong> — zetapay.com</p>
                    </div>
                  </div>
                </div>
                </body></html>
                """.formatted(link.getTitle(), amt, expiry, payUrl);
    }

    private String buildAbandonedCheckoutHtml(PaymentLink link, String payUrl) {
        String amt = formatAmount(link.getAmount(), link.getCurrency());
        return """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background:#f9fafb;">
                <div style="max-width:560px;margin:0 auto;padding:40px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                  <div style="text-align:center;margin-bottom:32px;">
                    <div style="display:inline-block;width:40px;height:40px;border-radius:10px;background:#4f46e5;line-height:40px;font-size:18px;color:white;font-weight:bold;">Z</div>
                  </div>
                  <div style="background:white;border-radius:16px;overflow:hidden;box-shadow:0 1px 3px rgba(0,0,0,0.08);">
                    <div style="background:#6366f1;padding:32px 32px 28px;text-align:center;">
                      <h1 style="color:white;font-size:22px;font-weight:700;margin:0 0 6px;letter-spacing:-0.5px;">Incomplete Payment</h1>
                      <p style="color:rgba(255,255,255,0.7);font-size:13px;margin:0;">You started but didn't finish</p>
                    </div>
                    <div style="padding:32px;text-align:center;">
                      <p style="color:#374151;font-size:15px;margin:0 0 8px;">You started paying for <strong style="color:#111827;">%s</strong> (%s) but did not finish.</p>
                      <div style="margin:24px 0;">
                        <a href="%s" style="display:inline-block;padding:14px 32px;background:#4f46e5;color:#fff;text-decoration:none;border-radius:10px;font-weight:600;font-size:15px;">Complete Payment</a>
                      </div>
                    </div>
                    <div style="border-top:1px solid #e5e7eb;padding:20px 32px;text-align:center;">
                      <p style="color:#9ca3af;font-size:11px;margin:0;">Secured by <strong style="color:#6b7280;">Zetapay</strong> — zetapay.com</p>
                    </div>
                  </div>
                </div>
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
