package com.zentrapay.service;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Email Service
 *
 * Handles all outgoing emails using Resend.
 *
 * Why Resend?
 * - Simple API (easier than JavaMail)
 * - Great deliverability (emails don't go to spam)
 * - Free tier: 3,000 emails/month
 *
 * Current emails:
 * - Email verification (when user registers)
 *
 * Future emails (we'll add later):
 * - Payment received notification
 * - Payment receipt to customer
 */
@Service
@Slf4j
public class EmailService {

    // Resend client - initialized once with API key
    private final Resend resend;

    // Where emails come from (e.g., noreply@zentrapay.com)
    @Value("${resend.from-email}")
    private String fromEmail;

    // Base URL for building links in emails
    // Development: http://localhost:8080
    // Production: https://api.zentrapay.com
    @Value("${app.base-url}")
    private String baseUrl;

    /**
     * Constructor - Spring injects the API key from .env
     * We create the Resend client here once
     */
    public EmailService(@Value("${resend.api-key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    /**
     * Send email verification link to new user
     *
     * Called after registration.
     * User receives an email with a link like:
     * http://localhost:8080/api/v1/auth/verify?token=abc123...
     *
     * @param toEmail   User's email address
     * @param fullName  User's full name (for personalization)
     * @param token     The verification token to include in link
     */
    public void sendVerificationEmail(String toEmail,
                                      String fullName,
                                      String token) {

        // Build the full verification URL
        String verificationUrl = baseUrl
                + "/api/v1/auth/verify?token="
                + token;

        log.info("Sending verification email to: {}", toEmail);

        try {
            // Build the email
            CreateEmailOptions email = CreateEmailOptions.builder()
                    .from(fromEmail)
                    .to(toEmail)
                    .subject("Verify your ZentraPay email address")
                    .html(buildVerificationEmailHtml(fullName, verificationUrl))
                    .build();

            // Send it via Resend
            resend.emails().send(email);

            log.info("Verification email sent successfully to: {}", toEmail);

        } catch (ResendException e) {
            // IMPORTANT: We log the error but don't crash the registration
            // Why? If email fails, user is still registered.
            // They can request a new verification email later.
            log.error("Failed to send verification email to {}: {}",
                    toEmail, e.getMessage());
        }
    }

    /**
     * Build the HTML content for verification email
     *
     * Kept simple and professional.
     * No external CSS frameworks - email clients are limited.
     *
     * @param fullName        User's name for greeting
     * @param verificationUrl Full URL with token
     * @return HTML string
     */
    private String buildVerificationEmailHtml(String fullName,
                                              String verificationUrl) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; 
                             max-width: 600px; 
                             margin: 0 auto; 
                             padding: 20px;">
                
                    <h2 style="color: #1a1a1a;">
                        Welcome to ZentraPay, %s!
                    </h2>
                    
                    <p style="color: #444; font-size: 16px;">
                        Thank you for registering. Please verify your 
                        email address to activate your account.
                    </p>
                    
                    <a href="%s"
                       style="display: inline-block;
                              padding: 14px 28px;
                              background-color: #0070f3;
                              color: white;
                              text-decoration: none;
                              border-radius: 6px;
                              font-weight: bold;
                              font-size: 16px;
                              margin: 20px 0;">
                        Verify Email Address
                    </a>
                    
                    <p style="color: #666; font-size: 14px;">
                        This link expires in <strong>24 hours</strong>.
                    </p>
                    
                    <p style="color: #666; font-size: 14px;">
                        If you did not create a ZentraPay account, 
                        you can safely ignore this email.
                    </p>
                    
                    <hr style="border: none; 
                               border-top: 1px solid #eee; 
                               margin: 30px 0;">
                    
                    <p style="color: #999; font-size: 12px;">
                        ZentraPay — Your central hub for payment collection
                    </p>
                    
                </body>
                </html>
                """.formatted(fullName, verificationUrl);
    }
}