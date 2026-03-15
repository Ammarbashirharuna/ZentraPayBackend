package com.zentrapay.exception;

/**
 * Thrown when a requested resource doesn't exist
 * Example: User not found, Payment link not found
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}