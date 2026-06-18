package com.interviewengine.evaluation;

import com.interviewengine.domain.Competency;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.Rubric;
import com.interviewengine.linguistics.LinguisticFeatures;
import com.interviewengine.linguistics.RuleBasedLinguisticAnalyzer;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end на реальном Gemini: один индикатор → один вызов LLM → IndicatorEvaluation
 * (веха 3 CLAUDE.md §12).
 *
 * <p>Запускается только если задан GEMINI_API_KEY — иначе пропускается, как обычный тест.
 * Использует «слабый» ответ кандидата (без личного вклада, без метрик) и индикатор
 * {@code ind_first_person} из senior-go-developer.yaml, чтобы LLM поставил низкий балл,
 * а acceptable = false. Это конкретное поведение проверяемо, а не «лишь бы что-то вернул».
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GEMINI_API_KEY", matches = ".+")
class SpringAiLlmJudgeIntegrationTest {

    @Autowired
    private LlmJudge judge;

    @Autowired
    private RubricLoader rubricLoader;

    @Test
    void обезличенныйОтветПолучаетНизкийБаллПоIndFirstPerson() {
        Rubric rubric = rubricLoader.loadByPosition("senior-go-developer");
        Indicator indFirstPerson = rubric.competencies().stream()
                .flatMap(c -> c.indicators().stream())
                .filter(i -> i.id().equals("ind_first_person"))
                .findFirst()
                .orElseThrow();

        String impersonalAnswer = """
                Команда занималась этой задачей. Мы вместе её обсудили, потом нам поставили сроки,
                и в итоге всё было сделано. Команда довольна результатом.
                """;

        LinguisticFeatures features = new RuleBasedLinguisticAnalyzer().analyze(impersonalAnswer);

        IndicatorEvaluation eval = judge.evaluate(indFirstPerson, impersonalAnswer, features, null);

        assertNotNull(eval);
        assertEquals("ind_first_person", eval.indicatorId(),
                "LLM обязан вернуть тот же indicatorId");
        assertTrue(eval.score() >= 0 && eval.score() <= 5,
                "score должен быть 0..5, получено " + eval.score());
        assertEquals(eval.score() >= indFirstPerson.acceptableFrom(), eval.acceptable(),
                "acceptable должен соответствовать порогу " + indFirstPerson.acceptableFrom());
        assertTrue(eval.score() <= 2,
                "обезличенный ответ должен получить низкий балл по ind_first_person, " +
                        "получено " + eval.score() + " (rationale: " + eval.rationale() + ")");
        assertNotNull(eval.rationale());
    }

    @Test
    void личныйОтветСМетрикамиПолучаетВысокийБаллПоIndMetrics() {
        Rubric rubric = rubricLoader.loadByPosition("senior-go-developer");
        Indicator indMetrics = findIndicator(rubric, "ind_metrics");

        String strongAnswer = """
                Я переписал критичный обработчик заказов с синхронного на пайплайн на каналах.
                Сократил p99-латентность с 850 мс до 120 мс — это замер на проде за неделю до и
                неделю после. RPS вырос с 1.2 тыс до 4 тыс на тех же ресурсах.
                """;

        LinguisticFeatures features = new RuleBasedLinguisticAnalyzer().analyze(strongAnswer);
        assertTrue(features.hasMetrics(), "признаки должны фиксировать наличие метрик в ответе");

        IndicatorEvaluation eval = judge.evaluate(indMetrics, strongAnswer, features, null);

        assertEquals("ind_metrics", eval.indicatorId());
        assertTrue(eval.score() >= 4,
                "ответ с конкретными метриками до/после должен получить >=4, получено " +
                        eval.score() + " (rationale: " + eval.rationale() + ")");
        assertTrue(eval.acceptable());
        assertTrue(eval.evidenceQuote() != null && !eval.evidenceQuote().isBlank(),
                "должна быть непустая цитата-доказательство");
    }

    private static Indicator findIndicator(Rubric rubric, String id) {
        return rubric.competencies().stream()
                .flatMap((Competency c) -> c.indicators().stream())
                .filter(i -> i.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
