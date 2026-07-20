package com.zentrapay.dto.payment;

import com.zentrapay.entity.Payment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A payment as returned to the seller who owns the link it was made against.
 *
 * Includes the fee breakdown (platform fee and the seller's net) so a seller
 * can see exactly what they will be paid out for a completed payment. The
 * breakdown is computed with the same basis-points rate used at settlement.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private UUID id;
    private UUID paymentLinkId;
    private String paymentLinkShortCode;
    private String customerEmail;
    /** Gross amount the customer paid, in minor units. */
    private Long amount;
    /** Platform fee withheld, in minor units. */
    private Long platformFee;
    /** What the seller is/was paid out (amount - platformFee), in minor units. */
    private Long netAmount;
    private String currency;
    private String providerReference;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;

    public static PaymentResponse from(Payment payment, long feeBasisPoints) {
        long fee = payment.getAmount() * feeBasisPoints / 10_000L;
        long net = payment.getAmount() - fee;
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentLinkId(payment.getPaymentLink() != null ? payment.getPaymentLink().getId() : null)
                .paymentLinkShortCode(payment.getPaymentLink() != null
                        ? payment.getPaymentLink().getShortCode() : null)
                .customerEmail(payment.getCustomerEmail())
                .amount(payment.getAmount())
                .platformFee(fee)
                .netAmount(net)
                .currency(payment.getCurrency())
                .providerReference(payment.getProviderReference())
                .status(payment.getStatus() != null ? payment.getStatus().name() : null)
                .paidAt(payment.getPaidAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
