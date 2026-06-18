package com.interviewengine.domain;

import java.util.List;

public record Rubric(
        String position,
        int durationMinutes,
        List<InterviewBlock> blocks,
        List<Competency> competencies
) {
    public Rubric {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        competencies = competencies == null ? List.of() : List.copyOf(competencies);
    }
}
