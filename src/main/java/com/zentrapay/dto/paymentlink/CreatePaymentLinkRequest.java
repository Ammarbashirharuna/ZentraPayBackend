package com.zentrapay.dto.paymentlink;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Request to create a payment link.
 *
 * Amount is in minor units (kobo/cents) and must be positive. Currency is
 * validated against the set the platform supports.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePaymentLinkRequest {

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be at most 200 characters")
    private String title;

    private String description;

    /** Amount in minor units (e.g. kobo, cents). */
    @NotNull(message = "Amount is required")
    @Min(value = 1, message = "Amount must be greater than zero")
    private Long amount;

    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^(NGN|USD|GHS|KES|ZAR)$",
            message = "Currency must be one of NGN, USD, GHS, KES, ZAR")
    private String currency;

    /** If true, the link is paid at most once. */
    @Builder.Default
    private Boolean singleUse = false;

    /** Optional cap on total payments. */
    @Min(value = 1, message = "maxUses must be at least 1")
    private Integer maxUses;

    /** Optional expiry timestamp. */
    private LocalDateTime expiresAt;

    /** Optional post-payment redirect URL. */
    private String redirectUrl;
}
