package com.zentrapay.service;

import com.zentrapay.dto.referral.ReferralResponse;
import com.zentrapay.entity.Referral;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.ReferralRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Referral program service.
 *
 * Each seller gets a unique referral code on demand. When a new seller
 * registers with a referral code, the referrer's usedCount is incremented.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;

    private final ReferralRepository referralRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** Get or create the current seller's referral code. */
    @Transactional
    public ReferralResponse getMyReferral() {
        User user = getCurrentUser();
        Referral referral = referralRepository.findByUserId(user.getId())
                .orElseGet(() -> createReferral(user));
        return toResponse(referral);
    }

    /** Apply a referral code during registration (called from AuthService). */
    @Transactional
    public void applyReferralCode(UUID newUserId, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) {
            return;
        }
        referralRepository.findByReferralCodeIgnoreCase(referralCode.trim())
                .ifPresent(referral -> {
                    referral.setUsedCount(referral.getUsedCount() + 1);
                    referralRepository.save(referral);
                    log.info("Referral code {} applied for new user {}", referralCode, newUserId);
                });
    }

    private Referral createReferral(User user) {
        String code = generateUniqueCode();
        Referral referral = Referral.builder()
                .userId(user.getId())
                .referralCode(code)
                .usedCount(0)
                .totalEarnings(0L)
                .build();
        return referralRepository.save(referral);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder sb = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                sb.append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)]);
            }
            String code = sb.toString();
            if (!referralRepository.findByReferralCodeIgnoreCase(code).isPresent()) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate unique referral code");
    }

    private ReferralResponse toResponse(Referral referral) {
        String base = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
        return ReferralResponse.builder()
                .referralCode(referral.getReferralCode())
                .referralUrl(base + "/register?ref=" + referral.getReferralCode())
                .usedCount(referral.getUsedCount())
                .totalEarnings(referral.getTotalEarnings())
                .createdAt(referral.getCreatedAt())
                .build();
    }
}
