package com.zentrapay.repository;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderReference(String providerReference);

    boolean existsByProviderReference(String providerReference);

    Page<Payment> findByPaymentLinkUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Payment> findByPaymentLinkUserIdAndStatusOrderByCreatedAtDesc(
            UUID userId, PaymentStatus status, Pageable pageable);

    Page<Payment> findByPaymentLinkIdAndPaymentLinkUserIdOrderByCreatedAtDesc(
            UUID paymentLinkId, UUID userId, Pageable pageable);

    Optional<Payment> findByIdAndPaymentLinkUserId(UUID id, UUID userId);

    Optional<Payment> findFirstByPaymentLinkIdAndCustomerEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
            UUID paymentLinkId, String customerEmail, PaymentStatus status);

    Page<Payment> findByPaymentLinkIdAndStatusOrderByCreatedAtDesc(
            UUID paymentLinkId, PaymentStatus status, Pageable pageable);

    @Query("SELECT p.currency AS currency, SUM(p.amount) AS gross, COUNT(p) AS cnt " +
            "FROM Payment p WHERE p.paymentLink.user.id = :userId AND p.status = 'COMPLETED' GROUP BY p.currency")
    List<Object[]> aggregateCompletedByCurrency(@Param("userId") UUID userId);

    @Query("SELECT p.currency, COUNT(p) FROM Payment p WHERE p.paymentLink.user.id = :userId AND p.status = 'PENDING' GROUP BY p.currency")
    List<Object[]> countPendingByCurrency(@Param("userId") UUID userId);

    @Query("SELECT p.currency, COUNT(p) FROM Payment p WHERE p.paymentLink.user.id = :userId AND p.status = 'FAILED' GROUP BY p.currency")
    List<Object[]> countFailedByCurrency(@Param("userId") UUID userId);

    @Query("SELECT FUNCTION('TO_CHAR', p.paidAt, 'YYYY-MM-DD') AS day, SUM(p.amount) AS revenue, COUNT(p) AS cnt " +
            "FROM Payment p WHERE p.paymentLink.user.id = :userId AND p.status = 'COMPLETED' AND p.paidAt >= :since GROUP BY day ORDER BY day")
    List<Object[]> dailyRevenueSince(@Param("userId") UUID userId, @Param("since") LocalDateTime since);

    @Query("SELECT p.paymentLink.shortCode, p.paymentLink.title, " +
            "SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), " +
            "COUNT(CASE WHEN p.status = 'COMPLETED' THEN 1 END), COUNT(p) " +
            "FROM Payment p WHERE p.paymentLink.user.id = :userId GROUP BY p.paymentLink.shortCode, p.paymentLink.title")
    List<Object[]> perLinkStats(@Param("userId") UUID userId);

    long countByPaymentLinkUserIdAndStatus(UUID userId, PaymentStatus status);

    long countByPaymentLinkUserId(UUID userId);

    @Query("SELECT AVG(p.amount) FROM Payment p WHERE p.paymentLink.user.id = :userId AND p.status = 'COMPLETED'")
    Double averagePaymentAmount(@Param("userId") UUID userId);

    @Query("SELECT p.paymentLink FROM Payment p WHERE p.status = 'PENDING' " +
            "AND p.paymentLink.status = 'ACTIVE' AND p.paymentLink.expiresAt IS NOT NULL " +
            "AND p.paymentLink.expiresAt BETWEEN :now AND :deadline")
    List<Payment> findExpiringPendingPayments(@Param("now") LocalDateTime now, @Param("deadline") LocalDateTime deadline);
}
