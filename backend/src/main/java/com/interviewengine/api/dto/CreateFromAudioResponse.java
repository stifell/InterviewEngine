package com.interviewengine.api.dto;

import com.interviewengine.domain.InterviewStatus;

import java.util.UUID;

/**
 * Ответ {@code POST /api/interviews/from-audio}. Возвращает id созданного интервью
 * и его текущий статус (обычно {@code TRANSCRIBING}). Дальше клиент поллит
 * {@code GET /api/interviews/{id}}.
 */
public record CreateFromAudioResponse(
        UUID interviewId,
        InterviewStatus status
) {
}
