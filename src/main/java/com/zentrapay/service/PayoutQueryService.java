package com.zentrapay.service;

import com.zentrapay.dto.payout.PayoutResponse;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PayoutRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only queries over a seller's settlements. Scoped through the settled
 * payment's link owner, so a seller only ever sees their own payouts.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayoutQueryService {

    private final PayoutRepository payoutRepository;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public Page<PayoutResponse> listMyPayouts(Pageable pageable) {
        UUID userId = getCurrentUser().getId();
        return payoutRepository
                .findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(PayoutResponse::from);
    }

    public PayoutResponse getMyPayout(UUID id) {
        UUID userId = getCurrentUser().getId();
        return payoutRepository.findByIdAndPaymentPaymentLinkUserId(id, userId)
                .map(PayoutResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Payout not found"));
    }
}
