package com.zentrapay.entity;

/**
 * How a seller receives their money. Pan-African markets rely on more than
 * bank accounts — mobile money dominates in East/West Africa.
 */
public enum PayoutMethod {
    /** Traditional bank account (most currencies). */
    BANK_ACCOUNT,
    /** Mobile money wallet (e.g. M-Pesa, MTN MoMo). */
    MOBILE_MONEY,
    /** Electronic funds transfer rail (e.g. ZAR). */
    EFT
}

