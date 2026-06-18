package com.interviewengine.api.dto;

import com.interviewengine.domain.InterviewStatus;
import com.interviewengine.domain.TranscriptSegment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ответ {@code GET /api/interviews/{id}}: статус + сырой транскрипт.
 * Результат оценки отдаётся отдельным эндпоинтом, чтобы клиент мог поллить статус,
 * не таская тяжёлый Scorecard.
 */
public record InterviewView(
        UUID id,
        String position,
        InterviewStatus status,
        List<TranscriptSegment> segments,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
}
