package com.interviewengine.api.dto;

import com.interviewengine.domain.TranscriptSegment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Запрос {@code POST /api/interviews}: позиция (id рубрикатора) + speaker- и block-tagged транскрипт.
 * <p>Аудио/видео ingest появится в вехе 8, тогда транскрипт станет опциональным.
 */
public record CreateInterviewRequest(
        @NotBlank String position,
        @NotEmpty @Valid List<TranscriptSegment> segments
) {
}
