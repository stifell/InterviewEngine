package com.interviewengine.domain;

import java.util.List;

/**
 * Скоркарта кандидата: оценки по всем компетенциям + рекомендация (шаг 9 конвейера).
 *
 * @param position       позиция (id рубрикатора)
 * @param overallScore   взвешенный итоговый балл 0..5
 * @param recommendation итоговая рекомендация
 * @param competencies   оценки по каждой компетенции
 */
public record Scorecard(
        String position,
        double overallScore,
        Recommendation recommendation,
        List<CompetencyEvaluation> competencies
) {
    public Scorecard {
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
    }
}
