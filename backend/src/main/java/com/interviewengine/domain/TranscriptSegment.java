package com.interviewengine.domain;

/**
 * Один сегмент транскрипта: реплика одного спикера, уже привязанная к блоку интервью
 * (шаги 2–3 конвейера выполнены).
 *
 * <p>Таймстемпы {@code startMs} / {@code endMs} опциональны: если транскрипт пришёл
 * из готового файла без времени, остаются {@code null}. Если есть — оценка интервьюера
 * (шаг 8) посчитает фактическую длительность каждого блока и сравнит с планом из рубрикатора.
 *
 * @param speaker  кто говорит
 * @param blockId  id блока рубрикатора, к которому относится реплика
 * @param text     текст реплики
 * @param startMs  миллисекунды от начала записи до начала реплики (nullable)
 * @param endMs    миллисекунды от начала записи до конца реплики (nullable)
 * @param prosody  просодика реплики (nullable: только для аудио через сайдкар)
 */
public record TranscriptSegment(
        SpeakerRole speaker,
        String blockId,
        String text,
        Long startMs,
        Long endMs,
        ProsodicFeatures prosody
) {

    /**
     * Совместимый с вехой 4 конструктор без таймстемпов. Используется в тестах и при
     * приёме транскриптов без временных меток.
     */
    public TranscriptSegment(SpeakerRole speaker, String blockId, String text) {
        this(speaker, blockId, text, null, null, null);
    }

    /** Конструктор с таймстемпами, но без просодики (текстовые/Gemini-транскрипты). */
    public TranscriptSegment(SpeakerRole speaker, String blockId, String text, Long startMs, Long endMs) {
        this(speaker, blockId, text, startMs, endMs, null);
    }
}
