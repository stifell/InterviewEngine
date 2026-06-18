package com.interviewengine.domain;

import java.util.List;

public record Competency(
        String id,
        String title,
        String block,
        double weight,
        List<Indicator> indicators
) {
    public Competency {
        indicators = indicators == null ? List.of() : List.copyOf(indicators);
    }
}
