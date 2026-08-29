package com.zentrapay.service;

import com.zentrapay.dto.paymentlink.CreatePaymentLinkRequest;
import com.zentrapay.dto.paymentlink.PaymentLinkResponse;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PayoutAccountRepository;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Business logic for creating and managing payment links.
 *
 * A seller must have an active, validated payout account before creating a
 * link — otherwise there is nowhere to settle the money. Short codes are
 * generated with a CSPRNG and retried on the (rare) collision.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLinkService {

    /** Unambiguous alphabet (no 0/O/1/I/l) for human-friendly short codes. */
    private static final char[] CODE_ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz".toCharArray();
    private static final int CODE_LENGTH = 7;
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final PaymentLinkRepository paymentLinkRepository;
    private final PayoutAccountRepository payoutAccountRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    public PaymentLinkResponse createPaymentLink(CreatePaymentLinkRequest request) {
        User currentUser = getCurrentUser();

        PayoutAccount payoutAccount = payoutAccountRepository
                .findByUserIdAndIsActiveTrue(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Set up an active payout account before creating payment links."));

        if (Boolean.FALSE.equals(payoutAccount.getAccountValidated())) {
            throw new IllegalStateException(
                    "Your payout account is not validated yet.");
        }

        PaymentLink link = PaymentLink.builder()
                .user(currentUser)
                .payoutAccount(payoutAccount)
                .shortCode(generateUniqueShortCode())
                .title(request.getTitle())
                .description(request.getDescription())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentLinkStatus.ACTIVE)
                .singleUse(Boolean.TRUE.equals(request.getSingleUse()))
                .maxUses(request.getMaxUses())
                .currentUses(0)
                .expiresAt(request.getExpiresAt())
                .redirectUrl(request.getRedirectUrl())
                .logoUrl(request.getLogoUrl())
                .brandColor(request.getBrandColor())
                .accentColor(request.getAccentColor())
                .thankYouMessage(request.getThankYouMessage())
                .build();

        PaymentLink saved = paymentLinkRepository.save(link);
        log.info("Payment link {} created by user {}", saved.getShortCode(), currentUser.getId());
        return toResponse(saved);
    }

    public Page<PaymentLinkResponse> listMyLinks(Pageable pageable) {
        User currentUser = getCurrentUser();
        return paymentLinkRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable)
                .map(this::toResponse);
    }

    public PaymentLinkResponse getMyLink(UUID id) {
        User currentUser = getCurrentUser();
        PaymentLink link = paymentLinkRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));
        return toResponse(link);
    }

    /** Soft-delete: mark the link DELETED so it can no longer be paid. */
    @Transactional
    public void deleteMyLink(UUID id) {
        User currentUser = getCurrentUser();
        PaymentLink link = paymentLinkRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment link not found"));
        link.setStatus(PaymentLinkStatus.DELETED);
        paymentLinkRepository.save(link);
        log.info("Payment link {} deleted by user {}", link.getShortCode(), currentUser.getId());
    }

    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!paymentLinkRepository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique payment link code, please retry.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
        }
        return sb.toString();
    }

    private PaymentLinkResponse toResponse(PaymentLink link) {
        String base = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        String url = base + "/api/v1/pay/" + link.getShortCode();
        return PaymentLinkResponse.from(link, url);
    }
}
