package com.zentrapay.integration;

import com.zentrapay.entity.Payment;
import com.zentrapay.entity.PaymentLink;
import com.zentrapay.entity.PaymentLinkStatus;
import com.zentrapay.entity.PaymentStatus;
import com.zentrapay.entity.Payout;
import com.zentrapay.entity.PayoutAccount;
import com.zentrapay.entity.PayoutMethod;
import com.zentrapay.entity.PayoutStatus;
import com.zentrapay.entity.User;
import com.zentrapay.repository.PaymentLinkRepository;
import com.zentrapay.repository.PaymentRepository;
import com.zentrapay.repository.PayoutAccountRepository;
import com.zentrapay.repository.PayoutRepository;
import com.zentrapay.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for payout persistence against the real Postgres schema
 * built by our Flyway migrations (V7). These verify the repository queries the
 * reconciliation job and seller read API rely on, plus the DB-level guarantees
 * (unique payment_id, status CHECK) that keep us from double-paying.
 */
class PayoutRepositoryIT extends AbstractIntegrationTest {

    @Autowired UserRepository userRepository;
    @Autowired PayoutAccountRepository payoutAccountRepository;
    @Autowired PaymentLinkRepository paymentLinkRepository;
    @Autowired PaymentRepository paymentRepository;
    @Autowired PayoutRepository payoutRepository;

    private User newUser() {
        User user = new User();
        user.setEmail("seller-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("x");
        user.setFullName("Ada Seller");
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    private PayoutAccount newAccount(User user) {
        return payoutAccountRepository.save(PayoutAccount.builder()
                .user(user).country("NG").currency("NGN")
                .method(PayoutMethod.BANK_ACCOUNT)
                .bankName("Test Bank").bankCode("058")
                .accountNumber("0123456789").accountName("Ada Seller")
                .accountValidated(true).isActive(true)
                .build());
    }

    private Payment newPayment(User user, PayoutAccount account, String ref) {
        PaymentLink link = paymentLinkRepository.save(PaymentLink.builder()
                .user(user).payoutAccount(account)
                .shortCode(ref.substring(0, Math.min(8, ref.length())))
                .title("Test").amount(10_000L).currency("NGN")
                .status(PaymentLinkStatus.ACTIVE).singleUse(false).currentUses(0)
                .build());
        return paymentRepository.save(Payment.builder()
                .paymentLink(link).customerEmail("buyer@example.com")
                .amount(10_000L).currency("NGN")
                .providerReference(ref).status(PaymentStatus.COMPLETED)
                .build());
    }

    private Payout newPayout(Payment payment, PayoutAccount account,
                             PayoutStatus status, Integer attempts, LocalDateTime lastAttempt) {
        return payoutRepository.save(Payout.builder()
                .payment(payment).payoutAccount(account)
                .reference("PO-" + payment.getProviderReference())
                .amount(9_900L).currency("NGN")
                .status(status).attempts(attempts).lastAttemptAt(lastAttempt)
                .build());
    }

    @Test
    void persistsAndReadsBackPayout() {
        User user = newUser();
        PayoutAccount account = newAccount(user);
        Payment payment = newPayment(user, account, "ZP-persist-1");

        Payout saved = newPayout(payment, account, PayoutStatus.PENDING, 0, null);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(payoutRepository.findByReference("PO-ZP-persist-1")).isPresent();
        assertThat(payoutRepository.existsByPaymentId(payment.getId())).isTrue();
    }

    @Test
    void enforcesOnePayoutPerPayment() {
        User user = newUser();
        PayoutAccount account = newAccount(user);
        Payment payment = newPayment(user, account, "ZP-unique-1");
        newPayout(payment, account, PayoutStatus.PENDING, 0, null);

        // A second payout for the same payment violates the UNIQUE(payment_id)
        // constraint — the DB stops us from ever settling a payment twice.
        assertThatThrownBy(() -> {
            payoutRepository.saveAndFlush(Payout.builder()
                    .payment(payment).payoutAccount(account)
                    .reference("PO-ZP-unique-1-dupe")
                    .amount(9_900L).currency("NGN")
                    .status(PayoutStatus.PENDING).attempts(0)
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsRetryableNeverAttemptedPayouts() {
        User user = newUser();
        PayoutAccount account = newAccount(user);
        Payment pending = newPayment(user, account, "ZP-neverattempt-1");
        newPayout(pending, account, PayoutStatus.PENDING, 0, null);

        List<Payout> due = payoutRepository
                .findByStatusInAndAttemptsLessThanAndLastAttemptAtIsNullOrderByCreatedAtAsc(
                        List.of(PayoutStatus.PENDING, PayoutStatus.FAILED), 6, Pageable.ofSize(50));

        assertThat(due).extracting(Payout::getReference).contains("PO-ZP-neverattempt-1");
    }

    @Test
    void findsFailedPayoutsPastBackoffButNotFreshOrExhausted() {
        User user = newUser();
        PayoutAccount account = newAccount(user);

        Payment stale = newPayment(user, account, "ZP-stale-1");
        newPayout(stale, account, PayoutStatus.FAILED, 1, LocalDateTime.now().minusHours(1));

        Payment fresh = newPayment(user, account, "ZP-fresh-1");
        newPayout(fresh, account, PayoutStatus.FAILED, 1, LocalDateTime.now());

        Payment exhausted = newPayment(user, account, "ZP-exhausted-1");
        newPayout(exhausted, account, PayoutStatus.FAILED, 6, LocalDateTime.now().minusHours(1));

        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<Payout> due = payoutRepository
                .findByStatusInAndAttemptsLessThanAndLastAttemptAtBeforeOrderByCreatedAtAsc(
                        List.of(PayoutStatus.PENDING, PayoutStatus.FAILED), 6, cutoff,
                        Pageable.ofSize(50));

        List<String> refs = due.stream().map(Payout::getReference).toList();
        assertThat(refs).contains("PO-ZP-stale-1");           // past backoff, retryable
        assertThat(refs).doesNotContain("PO-ZP-fresh-1");     // within backoff window
        assertThat(refs).doesNotContain("PO-ZP-exhausted-1"); // hit max attempts
    }

    @Test
    void scopesPayoutsToOwningSeller() {
        User seller = newUser();
        PayoutAccount account = newAccount(seller);
        Payment payment = newPayment(seller, account, "ZP-scope-1");
        newPayout(payment, account, PayoutStatus.PAID, 1, LocalDateTime.now());

        User other = newUser();

        Page<Payout> mine = payoutRepository
                .findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(seller.getId(), PageRequest.of(0, 10));
        Page<Payout> theirs = payoutRepository
                .findByPaymentPaymentLinkUserIdOrderByCreatedAtDesc(other.getId(), PageRequest.of(0, 10));

        assertThat(mine.getContent()).extracting(Payout::getReference).contains("PO-ZP-scope-1");
        assertThat(theirs.getContent()).isEmpty();
    }
}
