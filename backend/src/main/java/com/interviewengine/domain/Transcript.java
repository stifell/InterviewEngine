package com.interviewengine.domain;

import java.util.List;

/**
 * Вход в оценочный конвейер начиная с шага 4: speaker- и block-tagged транскрипт.
 */
public record Transcript(List<TranscriptSegment> segments) {

    public Transcript {
        segments = segments == null ? List.of() : List.copyOf(segments);
    }
}
