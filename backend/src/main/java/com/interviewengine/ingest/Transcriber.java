package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;

import java.util.List;

/**
 * Превращает медиа (аудио или видео) в speaker-tagged сегменты (§5.1 CLAUDE.md).
 *
 * <p>Сменный компонент: для быстрого старта — {@code GeminiTranscriber} (мультимодальный
 * вызов Gemini), для прода — {@code SidecarTranscriber} с GigaAM/WhisperX + pyannote.
 *
 * <p>Реализации не назначают роли (интервьюер/кандидат) и не привязывают к блокам —
 * это делают следующие шаги конвейера ({@code RoleAssigner}, {@code BlockClassifier}).
 */
public interface Transcriber {

    /**
     * @param media       сырые байты медиа (аудио или видео)
     * @param contentType MIME-тип ({@code audio/mp3}, {@code audio/wav}, {@code video/mp4} и т.д.)
     * @return сегменты в порядке записи
     */
    List<RawSpeakerSegment> transcribe(byte[] media, String contentType);
}
