package com.interviewengine.rubric;

import com.interviewengine.domain.Competency;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.Probe;
import com.interviewengine.domain.Rubric;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class RubricValidator {

    private static final double WEIGHT_TOLERANCE = 0.001;

    private final Rubric rubric;
    private final List<String> errors = new ArrayList<>();

    RubricValidator(Rubric rubric) {
        this.rubric = rubric;
    }

    void validate() {
        if (rubric.position() == null || rubric.position().isBlank()) {
            errors.add("position не задан");
        }
        if (rubric.blocks().isEmpty()) {
            errors.add("blocks пуст");
        }
        if (rubric.competencies().isEmpty()) {
            errors.add("competencies пуст");
        }

        Set<String> blockIds = collectUniqueIds(rubric.blocks(), InterviewBlock::id, "block");
        Set<String> indicatorIds = new HashSet<>();
        Set<String> competencyIds = new HashSet<>();

        for (Competency competency : rubric.competencies()) {
            if (!competencyIds.add(competency.id())) {
                errors.add("competency.id повторяется: " + competency.id());
            }
            if (competency.weight() < 0 || competency.weight() > 1) {
                errors.add("competency " + competency.id() + ": weight должен быть в [0,1], получено " + competency.weight());
            }
            if (competency.block() != null && !blockIds.contains(competency.block())) {
                errors.add("competency " + competency.id() + " ссылается на несуществующий block " + competency.block());
            }
            if (competency.indicators().isEmpty()) {
                errors.add("competency " + competency.id() + " без индикаторов");
            }
            for (Indicator indicator : competency.indicators()) {
                if (!indicatorIds.add(indicator.id())) {
                    errors.add("indicator.id повторяется: " + indicator.id());
                }
                validateIndicator(competency, indicator);
            }
        }

        double weightSum = rubric.competencies().stream().mapToDouble(Competency::weight).sum();
        if (Math.abs(weightSum - 1.0) > WEIGHT_TOLERANCE) {
            errors.add("Сумма весов компетенций должна равняться 1.0, получено " + weightSum);
        }

        Set<String> probeIds = new HashSet<>();
        for (InterviewBlock block : rubric.blocks()) {
            for (Probe probe : block.probes()) {
                if (!probeIds.add(probe.id())) {
                    errors.add("probe.id повторяется: " + probe.id());
                }
                if (probe.indicator() == null || probe.indicator().isBlank()) {
                    errors.add("probe " + probe.id() + ": indicator не задан (R3 — одна проба → один индикатор)");
                } else if (!indicatorIds.contains(probe.indicator())) {
                    errors.add("probe " + probe.id() + " ссылается на несуществующий indicator " + probe.indicator());
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new RubricLoadException("Рубрикатор невалиден:\n  - " + String.join("\n  - ", errors));
        }
    }

    private void validateIndicator(Competency competency, Indicator indicator) {
        String ctx = "indicator " + indicator.id() + " (competency " + competency.id() + ")";
        if (indicator.bars().isEmpty()) {
            errors.add(ctx + ": BARS пустой");
            return;
        }
        for (Integer level : indicator.bars().keySet()) {
            if (level < 0 || level > 5) {
                errors.add(ctx + ": BARS-уровень " + level + " вне диапазона 0..5");
            }
        }
        if (!indicator.bars().containsKey(0) || !indicator.bars().containsKey(5)) {
            errors.add(ctx + ": BARS должен описывать как минимум уровни 0 и 5");
        }
        if (indicator.acceptableFrom() < 0 || indicator.acceptableFrom() > 5) {
            errors.add(ctx + ": acceptableFrom вне диапазона 0..5");
        }
    }

    private static <T> Set<String> collectUniqueIds(List<T> items, java.util.function.Function<T, String> idFn, String kind) {
        Set<String> seen = new HashSet<>();
        for (T item : items) {
            String id = idFn.apply(item);
            if (id == null || id.isBlank()) {
                throw new RubricLoadException(kind + ".id не задан");
            }
            seen.add(id);
        }
        return seen;
    }
}
