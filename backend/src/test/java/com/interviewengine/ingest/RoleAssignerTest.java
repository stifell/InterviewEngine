package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleAssignerTest {

    private final RoleAssigner assigner = new RoleAssigner();
    private final Rubric rubric = new RubricLoader().loadByPosition("senior-go-developer");

    @Test
    void назначаетИнтервьюеромТогоКтоПопадаетВMainQuestion() {
        // spk0 читает близко к шаблонному mainQuestion блока 1
        // spk1 — кандидат, говорит свободно
        List<RawSpeakerSegment> raw = List.of(
                new RawSpeakerSegment("spk0", 0, 5000,
                        "Расскажите коротко о вашем опыте коммерческой разработки на Go: какие проекты, какие задачи решали"),
                new RawSpeakerSegment("spk1", 5000, 20000,
                        "Я работаю на Go шесть лет, писал биллинг и платежи."),
                new RawSpeakerSegment("spk0", 20000, 25000,
                        "Расскажите о самой сложной технической проблеме за последние полгода"),
                new RawSpeakerSegment("spk1", 25000, 40000,
                        "Я переписал обработчик заказов на каналах.")
        );

        Map<String, SpeakerRole> roles = assigner.assign(raw, rubric);

        assertEquals(SpeakerRole.INTERVIEWER, roles.get("spk0"));
        assertEquals(SpeakerRole.CANDIDATE, roles.get("spk1"));
    }

    @Test
    void одинСпикерСчитаетсяКандидатом() {
        List<RawSpeakerSegment> raw = List.of(
                new RawSpeakerSegment("spk0", 0, 5000, "Что-то рассказываю")
        );
        Map<String, SpeakerRole> roles = assigner.assign(raw, rubric);
        assertEquals(SpeakerRole.CANDIDATE, roles.get("spk0"));
        assertEquals(1, roles.size());
    }

    @Test
    void пустойСписокДаётПустоеНазначение() {
        assertTrue(assigner.assign(List.of(), rubric).isEmpty());
    }
}
