package com.interviewengine.domain;

import java.util.List;

/**
 * Оценка работы интервьюера (шаг 8 конвейера, §5 CLAUDE.md).
 *
 * @param mainQuestionCoverage  доля блоков, где у кандидата есть ответ на основной вопрос (0..1)
 * @param probeCoverage         доля запланированных проб, на которые получен непустой ответ (0..1)
 * @param scriptAdherence       доля реплик интервьюера, которые близки к шаблонным текстам
 *                              рубрикатора (jaccard ≥ порога) — насколько интервьюер «не отклонялся
 *                              от скрипта». В диапазоне 0..1, рассчитывается только по блокам, где
 *                              интервьюер вообще говорил.
 * @param missedProbeIds        id проб, к которым нет ответа кандидата
 * @param neutralityFlags       найденные проблемы нейтральности с цитатами-доказательствами
 * @param timingDeviations      отклонения по таймингу блоков (пуст, если у транскрипта нет таймстемпов)
 * @param notes                 произвольные наблюдения для будущих расширений
 */
public record InterviewerEvaluation(
        double mainQuestionCoverage,
        double probeCoverage,
        double scriptAdherence,
        List<String> missedProbeIds,
        List<NeutralityFlag> neutralityFlags,
        List<TimingDeviation> timingDeviations,
        List<String> notes
) {
    public InterviewerEvaluation {
        missedProbeIds = missedProbeIds == null ? List.of() : List.copyOf(missedProbeIds);
        neutralityFlags = neutralityFlags == null ? List.of() : List.copyOf(neutralityFlags);
        timingDeviations = timingDeviations == null ? List.of() : List.copyOf(timingDeviations);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
