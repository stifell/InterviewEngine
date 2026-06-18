package com.interviewengine.evaluation;

import com.interviewengine.domain.EvaluationResult;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.domain.Recommendation;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.linguistics.LinguisticFeatures;
import com.interviewengine.linguistics.RuleBasedLinguisticAnalyzer;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E2E-тест пайплайна с детерминированным фейком LlmJudge — без сети, без LLM (§11 CLAUDE.md).
 *
 * <p>Сценарий — «сильный» кандидат: в ответах есть личное «я», метрики, реальный
 * опыт. Фейк LlmJudge возвращает 5 для индикаторов, чьи источники-ответы содержат
 * «я», и 2 в остальных случаях — этого достаточно, чтобы проверить, что:
 * <ul>
 *   <li>правильный ответ ищется по правилам (probe.answer → fallback main.answer);</li>
 *   <li>лингвистические признаки реально передаются судье (захватываем их в spy);</li>
 *   <li>итоговая агрегация и рекомендация согласованы.</li>
 * </ul>
 */
class EvaluationPipelineTest {

    private final RubricLoader rubricLoader = new RubricLoader();
    private final AnswerAssembler answerAssembler = new AnswerAssembler();
    private final RuleBasedLinguisticAnalyzer analyzer = new RuleBasedLinguisticAnalyzer();
    private final ScoreAggregator aggregator = new ScoreAggregator();
    private final InterviewerEvaluator interviewerEvaluator = new InterviewerEvaluator();

    @Test
    void пайплайнЗапускаетсяНаTextТранскриптеИДаётСогласованныйРезультат() {
        RecordingFakeLlmJudge fakeJudge = new RecordingFakeLlmJudge();
        EvaluationPipeline pipeline = new EvaluationPipeline(
                rubricLoader, answerAssembler, analyzer, fakeJudge, aggregator, interviewerEvaluator);

        Transcript transcript = strongCandidateTranscript();

        EvaluationResult result = pipeline.evaluate("senior-go-developer", transcript);

        assertEquals("senior-go-developer", result.position());
        // все 4 компетенции рубрикатора должны попасть в скоркарту
        assertEquals(4, result.scorecard().competencies().size());
        // у нашего кандидата с «я» во всех ответах — фейк ставит 5 → STRONG_HIRE
        assertEquals(Recommendation.STRONG_HIRE, result.scorecard().recommendation());
        assertEquals(5.0, result.scorecard().overallScore(), 0.001);

        // судья был вызван по индикаторам, у которых есть источник-ответ
        assertFalse(fakeJudge.invocations.isEmpty());
        // признаки реально извлекались и передавались (не null)
        fakeJudge.invocations.forEach((id, call) -> {
            assertTrue(call.answer.contains("я") || call.answer.contains("Я"),
                    "answer для " + id + " должен содержать 1-е лицо: " + call.answer);
            assertTrue(call.features.hasFirstPerson(),
                    "features.hasFirstPerson должен быть true для " + id);
        });

        // покрытие интервьюера: все 7 проб заданы → 1.0
        assertEquals(1.0, result.interviewerEvaluation().probeCoverage(), 0.001);
        assertEquals(1.0, result.interviewerEvaluation().mainQuestionCoverage(), 0.001);
    }

    @Test
    void пайплайнНеЗоветСудьюЕслиОтветаНеБылоИИндикаторПомечаетсяКак0() {
        RecordingFakeLlmJudge fakeJudge = new RecordingFakeLlmJudge();
        EvaluationPipeline pipeline = new EvaluationPipeline(
                rubricLoader, answerAssembler, analyzer, fakeJudge, aggregator, interviewerEvaluator);

        // Транскрипт совсем пустой
        Transcript empty = new Transcript(List.of());

        EvaluationResult result = pipeline.evaluate("senior-go-developer", empty);

        assertTrue(fakeJudge.invocations.isEmpty(), "LLM-судья не должен звать при пустом транскрипте");
        assertEquals(Recommendation.NO_HIRE, result.scorecard().recommendation());
        // все индикаторы помечены как «не оценён»
        result.scorecard().competencies().forEach(c ->
                c.indicators().forEach(i ->
                        assertTrue(i.rationale().contains("не был оценён"))));
    }

    /**
     * Транскрипт «сильного» кандидата: содержательные ответы на все основные вопросы
     * и пробы из senior-go-developer.yaml. Везде есть «я» — чтобы фейковый судья
     * ставил высокие баллы.
     */
    private static Transcript strongCandidateTranscript() {
        List<TranscriptSegment> s = new ArrayList<>();

        // block1: icebreaker
        s.add(interv("block1", "Расскажите коротко об опыте на Go."));
        s.add(cand("block1", "Я работаю на Go уже шесть лет, я писал биллинг и платежи."));
        s.add(interv("block1", "Какова была ваша личная роль?"));
        s.add(cand("block1", "Я был техлидом и я отвечал за архитектуру сервиса."));

        // block2: STAR
        s.add(interv("block2", "Самая сложная проблема за полгода?"));
        s.add(cand("block2", "Я переписал критичный обработчик заказов на каналах."));
        s.add(interv("block2", "Ситуация и задача?"));
        s.add(cand("block2", "Я столкнулся с тем, что p99 рос до 850 мс под нагрузкой."));
        s.add(interv("block2", "Что делали именно вы?"));
        s.add(cand("block2", "Я профилировал, я нашёл узкое место, я переписал на pipeline."));
        s.add(interv("block2", "Результат в цифрах?"));
        s.add(cand("block2", "Я снизил p99 с 850 мс до 120 мс."));

        // block3: hard skills go
        s.add(interv("block3", "Расскажите про каналы и горутины."));
        s.add(cand("block3", "Я объясню. Я часто использую буферизованные каналы для пайплайнов."));
        s.add(interv("block3", "Что если писать в небуферизованный без читателя?"));
        s.add(cand("block3", "Я отвечу: писатель заблокируется, пока не появится читатель."));
        s.add(interv("block3", "Select с default и без?"));
        s.add(cand("block3", "Я использую default для неблокирующего polling."));
        s.add(interv("block3", "Опыт отладки гонок?"));
        s.add(cand("block3", "Я ловил гонку через go test -race в проде."));

        // block4: closing
        s.add(interv("block4", "Ваши вопросы?"));
        s.add(cand("block4", "Я хотел бы узнать про команду."));

        return new Transcript(s);
    }

    private static TranscriptSegment interv(String blockId, String text) {
        return new TranscriptSegment(SpeakerRole.INTERVIEWER, blockId, text);
    }

    private static TranscriptSegment cand(String blockId, String text) {
        return new TranscriptSegment(SpeakerRole.CANDIDATE, blockId, text);
    }

    /**
     * Фейк LlmJudge: ставит 5/acceptable для ответов с «я», 2/неприемлемо иначе.
     * Сохраняет каждый вызов, чтобы тест мог проверить, какие признаки и какой
     * текст ответа реально были переданы судье.
     */
    static class RecordingFakeLlmJudge implements LlmJudge {
        record Call(String answer, LinguisticFeatures features) {
        }

        final Map<String, Call> invocations = new ConcurrentHashMap<>();

        @Override
        public IndicatorEvaluation evaluate(Indicator indicator, String answer,
                                            LinguisticFeatures features, ProsodicFeatures prosody) {
            invocations.put(indicator.id(), new Call(answer, features));
            boolean strong = answer != null && (answer.contains("я") || answer.contains("Я"));
            int score = strong ? 5 : 2;
            return new IndicatorEvaluation(
                    indicator.id(),
                    score,
                    score >= indicator.acceptableFrom(),
                    strong ? "цитата с «я»" : "слабая цитата",
                    strong ? "сильный ответ" : "недостаточно личного вклада"
            );
        }
    }
}
