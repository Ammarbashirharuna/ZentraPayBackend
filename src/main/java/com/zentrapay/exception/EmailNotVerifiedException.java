package com.zentrapay.exception;

/**
 * Thrown when user tries to login but email is not verified
 */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String message) {
        super(message);
    }
}