package com.interviewengine.domain;

import java.util.List;
import java.util.Map;

public record Indicator(
        String id,
        String text,
        List<String> signals,
        /**
         * Доменные термины для расчёта {@code termCoverage} (лингвистический признак).
         * Поверхностные формы на русском (например, «горутина», «заблокируется»).
         * Если список пуст — признак в промпт не включается.
         * Ограничение v1: нет лемматизации, ищутся точные формы.
         */
        List<String> terms,
        Map<Integer, String> bars,
        int acceptableFrom
) {
    public Indicator {
        signals = signals == null ? List.of() : List.copyOf(signals);
        terms   = terms   == null ? List.of() : List.copyOf(terms);
        bars    = bars    == null ? Map.of()  : Map.copyOf(bars);
    }
}
