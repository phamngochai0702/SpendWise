package com.spendwise.service;

/**
 * Thrown when a user input violates a business rule. The message is written in
 * Vietnamese and is shown next to the relevant form field or in a dialog.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String userMessage) {
        super(userMessage);
    }
}
