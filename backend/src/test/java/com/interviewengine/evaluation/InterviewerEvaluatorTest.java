package com.interviewengine.evaluation;

import com.interviewengine.domain.InterviewerEvaluation;
import com.interviewengine.domain.NeutralityFlag;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.TimingDeviation;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InterviewerEvaluatorTest {

    private final InterviewerEvaluator evaluator = new InterviewerEvaluator();
    private final Rubric rubric = new RubricLoader().loadByPosition("senior-go-developer");

    @Test
    void пустыеОтветыДают0() {
        InterviewerEvaluation eval = evaluator.evaluate(
                new Transcript(List.of()), rubric, new AssembledAnswers(Map.of(), Map.of()));

        assertEquals(0.0, eval.mainQuestionCoverage(), 0.001);
        assertEquals(0.0, eval.probeCoverage(), 0.001);
        assertEquals(7, eval.missedProbeIds().size());
        // если интервьюер ничего не сказал — adherence по нулю реплик считается 1.0
        // (нет того, что отклонять)
        assertEquals(1.0, eval.scriptAdherence(), 0.001);
        assertTrue(eval.neutralityFlags().isEmpty());
        assertTrue(eval.timingDeviations().isEmpty());
    }

    @Test
    void всеОтветыДаютПолноеПокрытие() {
        Map<String, String> mains = Map.of(
                "block1", "ответ 1",
                "block2", "ответ 2",
                "block3", "ответ 3",
                "block4", "ответ 4");
        Map<String, String> probes = Map.of(
                "p1_1", "a",
                "p2_1", "a", "p2_2", "a", "p2_3", "a",
                "p3_1", "a", "p3_2", "a", "p3_3", "a");

        InterviewerEvaluation eval = evaluator.evaluate(
                new Transcript(List.of()), rubric, new AssembledAnswers(mains, probes));

        assertEquals(1.0, eval.mainQuestionCoverage(), 0.001);
        assertEquals(1.0, eval.probeCoverage(), 0.001);
        assertTrue(eval.missedProbeIds().isEmpty());
    }

    @Test
    void пустойОтветНаПробуСчитаетсяПропуском() {
        Map<String, String> probes = Map.of(
                "p1_1", "a",
                "p2_1", "  ",
                "p2_2", "ans");

        InterviewerEvaluation eval = evaluator.evaluate(
                new Transcript(List.of()), rubric,
                new AssembledAnswers(Map.of("block1", "main"), probes));

        assertTrue(eval.missedProbeIds().contains("p2_1"));
        assertEquals(2.0 / 7.0, eval.probeCoverage(), 0.001);
    }

    @Test
    void scriptAdherence_дословныйОсновнойВопросДаётВысокийПоказатель() {
        // block4 (Завершение) без проб; интервьюер зачитал его главный вопрос близко к шаблону.
        // Эталон: «Какие у вас остались вопросы по позиции и команде?»
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block4",
                        "Какие у вас остались вопросы по позиции и команде?")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertEquals(1.0, eval.scriptAdherence(), 0.001);
    }

    @Test
    void scriptAdherence_bestMatchНеЗависитОтПорядкаПроб() {
        // block2 = главный вопрос + 3 пробы. Интервьюер задаёт их ДОСЛОВНО, но в обратном
        // порядке. Позиционное сравнение дало бы ~0.5; best-match даёт 1.0.
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2",
                        "Расскажите о самой сложной технической проблеме, которую вы лично решили за последние полгода."),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "ответ"),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2",
                        "Какой измеримый результат получили? Назовите цифры — до и после."),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "ответ"),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2",
                        "Какие конкретно действия предприняли именно вы — что делали лично, а не команда?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "ответ"),
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2",
                        "Какова была изначальная ситуация и задача — что нужно было сделать и почему это было сложно?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "ответ")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertEquals(1.0, eval.scriptAdherence(), 0.001);
    }

    @Test
    void scriptAdherence_совсемДругойВопросДаётНоль() {
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block1", "Привет, как настроение сегодня?")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertEquals(0.0, eval.scriptAdherence(), 0.001);
    }

    @Test
    void нейтральностьЛовитSuggestiveAgreement() {
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block2",
                        "Каналы блокируют горутины, не так ли?"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block2", "Да.")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertFalse(eval.neutralityFlags().isEmpty());
        NeutralityFlag flag = eval.neutralityFlags().get(0);
        assertEquals(NeutralityFlag.Kind.SUGGESTIVE_AGREEMENT, flag.kind());
        assertEquals("block2", flag.blockId());
        assertTrue(flag.quote().contains("не так ли"));
    }

    @Test
    void нейтральностьЛовитAnswerHint() {
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block3",
                        "Вы, наверное, использовали sync.Mutex для защиты?")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        // словарь матчит «вы, наверное»
        assertTrue(eval.neutralityFlags().stream().anyMatch(f -> f.kind() == NeutralityFlag.Kind.ANSWER_HINT));
    }

    @Test
    void нейтральностьИгнорируетРеплкКандидата() {
        // Кандидат может говорить «не так ли» — это не должно считаться флагом
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "Мы сделали правильно, не так ли?")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertTrue(eval.neutralityFlags().isEmpty());
    }

    @Test
    void timingСчитаетсяТолькоПриНаличииТаймстемпов() {
        // block1 в рубрикаторе запланирован на 3 минуты, реально длился 4 минуты
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block1", "Q", 0L, 5_000L),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "A", 5_000L, 240_000L)
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));

        assertEquals(1, eval.timingDeviations().size());
        TimingDeviation dev = eval.timingDeviations().get(0);
        assertEquals("block1", dev.blockId());
        assertEquals(3, dev.plannedMinutes());
        assertEquals(4.0, dev.actualMinutes(), 0.01);
        assertEquals(1.0, dev.deviationMinutes(), 0.01);
    }

    @Test
    void timingПустойБезТаймстемпов() {
        Transcript t = new Transcript(List.of(
                new TranscriptSegment(SpeakerRole.INTERVIEWER, "block1", "Q")
        ));
        InterviewerEvaluation eval = evaluator.evaluate(t, rubric, new AssembledAnswers(Map.of(), Map.of()));
        assertTrue(eval.timingDeviations().isEmpty());
    }
}
