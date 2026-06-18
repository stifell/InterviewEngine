package com.interviewengine.domain;

import java.util.List;

public record InterviewBlock(
        String id,
        int order,
        String title,
        int timingMinutes,
        String mainQuestion,
        List<Probe> probes
) {
    public InterviewBlock {
        probes = probes == null ? List.of() : List.copyOf(probes);
    }
}
