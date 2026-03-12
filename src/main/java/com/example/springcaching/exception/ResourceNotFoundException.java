package com.example.springcaching.exception;

/**
 * Thrown when a requested resource cannot be found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceType, Long id) {
        super(resourceType + " not found with id: " + id);
    }
}
