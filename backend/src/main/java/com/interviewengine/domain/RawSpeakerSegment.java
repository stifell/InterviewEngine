package com.interviewengine.domain;

/**
 * Сырой сегмент транскрипта от ASR + диаризации: ещё нет ни роли интервьюер/кандидат,
 * ни привязки к блоку рубрикатора — только кластер спикера + текст + таймстемпы.
 * <p>Возвращается {@code Transcriber}. Дальше {@code RoleAssigner} и
 * {@code BlockClassifier} обогащают эти сегменты до полноценного {@link TranscriptSegment}.
 *
 * @param rawSpeakerId технический id кластера спикеров (например, «spk0», «spk1»)
 * @param startMs       начало в миллисекундах от начала записи
 * @param endMs         конец в миллисекундах от начала записи
 * @param text          расшифровка реплики
 * @param prosody       просодика этого сегмента из сайдкара; {@code null} для
 *                      текстовых интервью и {@code GeminiTranscriber}
 */
public record RawSpeakerSegment(
        String rawSpeakerId,
        long startMs,
        long endMs,
        String text,
        ProsodicFeatures prosody
) {

    /** Совместимый конструктор без просодики (тесты, транскрайберы без аудио-анализа). */
    public RawSpeakerSegment(String rawSpeakerId, long startMs, long endMs, String text) {
        this(rawSpeakerId, startMs, endMs, text, null);
    }
}
