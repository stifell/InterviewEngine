package com.interviewengine.rubric;

public class RubricLoadException extends RuntimeException {

    public RubricLoadException(String message) {
        super(message);
    }

    public RubricLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
