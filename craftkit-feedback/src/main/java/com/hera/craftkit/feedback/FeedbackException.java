package com.hera.craftkit.feedback;

public class FeedbackException extends RuntimeException {

    public FeedbackException(String message) {
        super(message);
    }

    public FeedbackException(String message, Throwable cause) {
        super(message, cause);
    }
}
