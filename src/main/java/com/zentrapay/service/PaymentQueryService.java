package com.zentrapay.service;

import com.zentrapay.dto.payment.PaymentResponse;
import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.User;
import com.zentrapay.exception.ResourceNotFoundException;
import com.zentrapay.repository.PaymentRepository;
import com.zentrapay.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Read-only queries over a seller's payments. Every query is scoped through the
 * link's owner, so a seller can only ever see payments made against their own
 * links.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;

    /** Same fee rate used at settlement, so the net shown matches the payout. */
    @Value("${platform.fee-basis-points:100}")
    private long feeBasisPoints;

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    /** List the seller's payments, optionally filtered by status. */
    public Page<PaymentResponse> listMyPayments(PaymentStatus status, Pageable pageable) {
        UUID userId = getCurrentUser().getId();
        Page<Payment> page = (status == null)
                ? paymentRepository.findByPaymentLinkUserIdOrderByCreatedAtDesc(userId, pageable)
                : paymentRepository.findByPaymentLinkUserIdAndStatusOrderByCreatedAtDesc(
                        userId, status, pageable);
        return page.map(p -> PaymentResponse.from(p, feeBasisPoints));
    }

    /** List payments against one of the seller's links. */
    public Page<PaymentResponse> listPaymentsForLink(UUID linkId, Pageable pageable) {
        UUID userId = getCurrentUser().getId();
        return paymentRepository
                .findByPaymentLinkIdAndPaymentLinkUserIdOrderByCreatedAtDesc(linkId, userId, pageable)
                .map(p -> PaymentResponse.from(p, feeBasisPoints));
    }

    /** Get one of the seller's payments by id. */
    public PaymentResponse getMyPayment(UUID id) {
        UUID userId = getCurrentUser().getId();
        Payment payment = paymentRepository.findByIdAndPaymentLinkUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return PaymentResponse.from(payment, feeBasisPoints);
    }
}
