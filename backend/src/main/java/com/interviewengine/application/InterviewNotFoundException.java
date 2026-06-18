package com.interviewengine.application;

import java.util.UUID;

public class InterviewNotFoundException extends RuntimeException {

    public InterviewNotFoundException(UUID id) {
        super("Интервью не найдено: " + id);
    }
}
