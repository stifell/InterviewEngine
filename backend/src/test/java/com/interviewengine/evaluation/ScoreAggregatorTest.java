package com.interviewengine.evaluation;

import com.interviewengine.domain.Competency;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.Recommendation;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.Scorecard;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreAggregatorTest {

    private final ScoreAggregator aggregator = new ScoreAggregator();
    private final Rubric rubric = new RubricLoader().loadByPosition("senior-go-developer");

    @Test
    void всеИндикаторы4АкуратноВыходитНаHire() {
        Map<String, IndicatorEvaluation> evals = allWithScore(3);
        Scorecard sc = aggregator.aggregate(rubric, evals);

        assertEquals(3.0, sc.overallScore(), 0.001);
        assertEquals(Recommendation.HIRE, sc.recommendation());
        sc.competencies().forEach(c -> assertTrue(c.acceptable()));
    }

    @Test
    void всеИндикаторы5ДаютStrongHire() {
        Map<String, IndicatorEvaluation> evals = allWithScore(5);
        Scorecard sc = aggregator.aggregate(rubric, evals);

        assertEquals(5.0, sc.overallScore(), 0.001);
        assertEquals(Recommendation.STRONG_HIRE, sc.recommendation());
    }

    @Test
    void одинКритичныйНеПрошедшийИндикаторДаётNoHire() {
        Map<String, IndicatorEvaluation> evals = allWithScore(4);
        // ind_concurrency_correctness — балл 1 (ниже acceptableFrom=3)
        evals.put("ind_concurrency_correctness",
                new IndicatorEvaluation("ind_concurrency_correctness", 1, false, "не знал", "ошибка"));
        Scorecard sc = aggregator.aggregate(rubric, evals);

        assertEquals(Recommendation.NO_HIRE, sc.recommendation());
        // компетенция concurrency должна быть not acceptable
        var concurrency = sc.competencies().stream()
                .filter(c -> c.competencyId().equals("comp_concurrency"))
                .findFirst().orElseThrow();
        assertFalse(concurrency.acceptable());
    }

    @Test
    void отсутствиеОценкиИндикатораТрактуетсяКакНеПройдено() {
        // ничего не оцениваем — нет оценок ни по одному индикатору
        Scorecard sc = aggregator.aggregate(rubric, Map.of());

        assertEquals(Recommendation.NO_HIRE, sc.recommendation());
        assertEquals(0.0, sc.overallScore(), 0.001);
        sc.competencies().forEach(c -> {
            assertFalse(c.acceptable());
            c.indicators().forEach(i -> {
                assertEquals(0, i.score());
                assertFalse(i.acceptable());
                assertTrue(i.rationale().contains("не был оценён"));
            });
        });
    }

    @Test
    void overallScoreВзвешиваетсяПоВесамКомпетенций() {
        // Дадим разные баллы по компетенциям — проверим, что итог именно взвешенный
        Map<String, IndicatorEvaluation> evals = allWithScore(3);
        // переопределим concurrency (вес 0.35) на балл 5
        rubric.competencies().stream()
                .filter(c -> c.id().equals("comp_concurrency"))
                .findFirst()
                .ifPresent(c -> c.indicators().forEach(i ->
                        evals.put(i.id(),
                                new IndicatorEvaluation(i.id(), 5, true, "q", "r"))));

        Scorecard sc = aggregator.aggregate(rubric, evals);

        // веса: ownership 0.25, results 0.15, concurrency 0.30, communication 0.15, speech_confidence 0.15
        // компетенции: 3, 3, 5, 3, 3 (просодические индикаторы здесь тоже оценены → домен учитывается)
        // overall = 0.25*3 + 0.15*3 + 0.30*5 + 0.15*3 + 0.15*3 = 0.75 + 0.45 + 1.50 + 0.45 + 0.45 = 3.60
        assertEquals(3.60, sc.overallScore(), 0.01);
    }

    @Test
    void компетенцияБезОценённыхИндикаторовИсключаетсяИзСкоркарты() {
        // Сценарий «текстовое интервью»: просодический домен comp_speech_confidence
        // не оценивался (нет аудио). Он не должен попасть в скоркарту и тянуть в NO_HIRE.
        Map<String, IndicatorEvaluation> evals = new HashMap<>();
        for (Competency c : rubric.competencies()) {
            if (c.id().equals("comp_speech_confidence")) {
                continue; // имитируем пропуск чисто-просодических индикаторов
            }
            for (var ind : c.indicators()) {
                evals.put(ind.id(), new IndicatorEvaluation(ind.id(), 4, true, "цитата", "ок"));
            }
        }

        Scorecard sc = aggregator.aggregate(rubric, evals);

        assertTrue(sc.competencies().stream()
                        .noneMatch(c -> c.competencyId().equals("comp_speech_confidence")),
                "домен без единого оценённого индикатора должен быть исключён");
        assertEquals(4, sc.competencies().size());
        assertEquals(Recommendation.STRONG_HIRE, sc.recommendation());
    }

    private Map<String, IndicatorEvaluation> allWithScore(int score) {
        Map<String, IndicatorEvaluation> m = new HashMap<>();
        for (Competency c : rubric.competencies()) {
            for (var ind : c.indicators()) {
                boolean acceptable = score >= ind.acceptableFrom();
                m.put(ind.id(), new IndicatorEvaluation(ind.id(), score, acceptable, "цитата", "пояснение"));
            }
        }
        return m;
    }
}
