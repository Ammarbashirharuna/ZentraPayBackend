package com.zentrapay.exception;

/**
 * Thrown when login credentials are incorrect
 * Example: Wrong password or email not found
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}