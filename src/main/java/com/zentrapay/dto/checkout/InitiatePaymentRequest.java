package com.zentrapay.dto.checkout;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Customer's request to start paying a link. The amount and currency come from
 * the link server-side — never from the client — so a customer can't pay less.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitiatePaymentRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "A valid email is required")
    private String customerEmail;
}
