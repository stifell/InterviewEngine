package com.interviewengine.api.dto;

import com.interviewengine.domain.InterviewStatus;

import java.util.UUID;

/**
 * Ответ {@code POST /api/interviews/{id}/evaluate}. Id задачи — это id самого интервью
 * (см. {@code InterviewService.startEvaluation}); статус читается через GET интервью.
 */
public record EvaluationStartedResponse(
        UUID taskId,
        UUID interviewId,
        InterviewStatus status
) {
}
