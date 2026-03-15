package com.zentrapay.exception;

/**
 * Thrown when trying to create a resource that already exists
 * Example: Registering with an email that's already in the database
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}