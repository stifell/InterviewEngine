package com.interviewengine.evaluation;

import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerAssemblerTest {

    private final AnswerAssembler assembler = new AnswerAssembler();
    private final Rubric rubric = new RubricLoader().loadByPosition("senior-go-developer");

    @Test
    void основнойОтветПишетсяКПервойРепликеИнтервьюераВБлоке() {
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block1", "Расскажите об опыте"),
                seg(SpeakerRole.CANDIDATE, "block1", "Шесть лет на Go"),
                seg(SpeakerRole.CANDIDATE, "block1", "Последний проект — биллинг.")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertEquals("Шесть лет на Go Последний проект — биллинг.", a.mainAnswer("block1"));
    }

    @Test
    void пробыНазначаютсяПоПорядкуВРубрикаторе() {
        // block2 имеет 3 пробы: p2_1, p2_2, p2_3 (см. senior-go-developer.yaml)
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block2", "Самая сложная задача?"),
                seg(SpeakerRole.CANDIDATE, "block2", "Главный ответ"),
                seg(SpeakerRole.INTERVIEWER, "block2", "Ситуация?"),
                seg(SpeakerRole.CANDIDATE, "block2", "Контекст: микросервис заказов"),
                seg(SpeakerRole.INTERVIEWER, "block2", "Что делали лично?"),
                seg(SpeakerRole.CANDIDATE, "block2", "Я переписал на каналы"),
                seg(SpeakerRole.INTERVIEWER, "block2", "Результат в цифрах?"),
                seg(SpeakerRole.CANDIDATE, "block2", "С 850 мс до 120 мс")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertEquals("Главный ответ", a.mainAnswer("block2"));
        assertEquals("Контекст: микросервис заказов", a.probeAnswer("p2_1"));
        assertEquals("Я переписал на каналы", a.probeAnswer("p2_2"));
        assertEquals("С 850 мс до 120 мс", a.probeAnswer("p2_3"));
    }

    @Test
    void пропущеннаяПробаОстаётсяБезОтвета() {
        // Интервьюер задал только основной + первую пробу
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block2", "Q"),
                seg(SpeakerRole.CANDIDATE, "block2", "main"),
                seg(SpeakerRole.INTERVIEWER, "block2", "P1"),
                seg(SpeakerRole.CANDIDATE, "block2", "ответ на p2_1")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertEquals("ответ на p2_1", a.probeAnswer("p2_1"));
        assertTrue(a.probeAnswer("p2_2").isBlank());
        assertTrue(a.probeAnswer("p2_3").isBlank());
    }

    @Test
    void репликаКандидатаДоИнтервьюераИгнорируется() {
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.CANDIDATE, "block1", "посторонняя реплика"),
                seg(SpeakerRole.INTERVIEWER, "block1", "Q"),
                seg(SpeakerRole.CANDIDATE, "block1", "настоящий ответ")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertEquals("настоящий ответ", a.mainAnswer("block1"));
    }

    @Test
    void блокБезПробНеЛомаетсяОтЛишнихХодовИнтервьюера() {
        // block4 (Завершение) — без проб; даже если интервьюер задаёт «лишние» вопросы,
        // эти ответы кандидата никуда не привязываются и не падают
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block4", "Вопросы?"),
                seg(SpeakerRole.CANDIDATE, "block4", "Какая команда?"),
                seg(SpeakerRole.INTERVIEWER, "block4", "5 человек"),
                seg(SpeakerRole.CANDIDATE, "block4", "лишний ответ")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertEquals("Какая команда?", a.mainAnswer("block4"));
        assertFalse(a.probeAnswerByProbeId().containsKey("doesnotexist"));
    }

    @Test
    void просодикаАгрегируетсяВзвешенноПоОсновномуОтвету() {
        // Две реплики кандидата на основной вопрос: темп 100 (1с) и 200 (3с) →
        // взвешенно по длительности = 175 слов/мин
        ProsodicFeatures p1 = prosody(100, 1000);
        ProsodicFeatures p2 = prosody(200, 3000);
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block1", "Вопрос"),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "часть один", 0L, 1000L, p1),
                new TranscriptSegment(SpeakerRole.CANDIDATE, "block1", "часть два", 1000L, 4000L, p2)
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        ProsodicFeatures agg = a.mainProsody("block1");
        assertNotNull(agg, "просодика основного ответа должна быть собрана");
        assertEquals(175.0, agg.speechRateWpm(), 0.001);
        assertEquals(4000L, agg.durationMs());
    }

    @Test
    void безПросодикиВСегментахОтветИмеетNullПросодику() {
        Transcript t = new Transcript(List.of(
                seg(SpeakerRole.INTERVIEWER, "block1", "Вопрос"),
                seg(SpeakerRole.CANDIDATE, "block1", "ответ без аудио")
        ));

        AssembledAnswers a = assembler.assemble(t, rubric);

        assertNull(a.mainProsody("block1"), "без просодики в сегментах ответ не должен иметь просодику");
    }

    private static ProsodicFeatures prosody(double speechRate, long durationMs) {
        return new ProsodicFeatures(
                speechRate, speechRate, 0.2, 1, 100, 150, 3.0, 60, 5, 0.8, durationMs);
    }

    private static TranscriptSegment seg(SpeakerRole role, String blockId, String text) {
        return new TranscriptSegment(role, blockId, text);
    }
}
