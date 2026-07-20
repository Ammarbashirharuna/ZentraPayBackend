package com.zentrapay.dto.checkout;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Where to send the customer to complete payment.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentResponse {

    /** Our transaction reference (idempotency key). */
    private String reference;
    /** Provider-hosted checkout URL to redirect the customer to. */
    private String checkoutUrl;
    /** Provider access code, for inline SDK flows. */
    private String accessCode;
}
