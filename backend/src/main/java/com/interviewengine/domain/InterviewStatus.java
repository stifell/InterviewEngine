package com.interviewengine.domain;

/**
 * Состояние процесса оценки интервью в системе.
 *
 * <ul>
 *   <li>{@code PENDING} — интервью создано, оценка ещё не запускалась.</li>
 *   <li>{@code TRANSCRIBING} — конвейер сейчас транскрибирует аудио/видео в транскрипт (шаги 1–3 §5).</li>
 *   <li>{@code RUNNING} — оценочный конвейер сейчас выполняется (шаги 4–9).</li>
 *   <li>{@code DONE} — оценка завершена, {@code resultJson} заполнен.</li>
 *   <li>{@code FAILED} — конвейер упал, причина в {@code errorMessage}.</li>
 * </ul>
 */
public enum InterviewStatus {
    PENDING,
    TRANSCRIBING,
    RUNNING,
    DONE,
    FAILED
}
