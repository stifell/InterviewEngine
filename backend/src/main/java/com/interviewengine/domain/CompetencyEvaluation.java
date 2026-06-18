package com.interviewengine.domain;

import java.util.List;

/**
 * Агрегированная оценка одной компетенции: взвешенный средний балл по её индикаторам
 * + расшифровка по индикаторам.
 *
 * @param competencyId id из рубрикатора
 * @param title        читаемое название
 * @param weight       вес компетенции из рубрикатора
 * @param score        взвешенный/средний балл 0..5
 * @param acceptable   все индикаторы прошли свой acceptableFrom
 * @param indicators   оценки по каждому индикатору этой компетенции
 */
public record CompetencyEvaluation(
        String competencyId,
        String title,
        double weight,
        double score,
        boolean acceptable,
        List<IndicatorEvaluation> indicators
) {
    public CompetencyEvaluation {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
