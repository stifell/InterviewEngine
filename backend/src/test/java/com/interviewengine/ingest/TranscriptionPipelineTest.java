package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.Transcript;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranscriptionPipelineTest {

    private final RubricLoader rubricLoader = new RubricLoader();
    private final RoleAssigner roleAssigner = new RoleAssigner();
    private final HeuristicBlockClassifier blockClassifier = new HeuristicBlockClassifier();

    @Test
    void полныйКонвейерСFakeTranscriberДаётГотовыйTranscript() {
        Transcriber fakeTranscriber = (media, contentType) -> List.of(
                new RawSpeakerSegment("spk0", 0, 2000, "Здравствуйте"), // прелюдия, без блока
                new RawSpeakerSegment("spk0", 2000, 7000,
                        "Расскажите коротко о вашем опыте коммерческой разработки на Go: какие проекты, какие задачи решали"),
                new RawSpeakerSegment("spk1", 7000, 15000, "Я работаю на Go шесть лет, я был техлидом"),
                new RawSpeakerSegment("spk0", 15000, 20000,
                        "Расскажите о самой сложной технической проблеме за полгода"),
                new RawSpeakerSegment("spk1", 20000, 30000, "Я переписал обработчик заказов на каналах")
        );

        TranscriptionPipeline pipeline = new TranscriptionPipeline(
                fakeTranscriber, roleAssigner, blockClassifier, rubricLoader);

        Transcript t = pipeline.transcribe(new byte[]{1, 2, 3}, "audio/mpeg", "senior-go-developer");

        // 1 прелюдийная реплика отброшена (без блока), остаётся 4
        assertEquals(4, t.segments().size());

        // первая попавшая в блок реплика — main question block1, говорил spk0 → INTERVIEWER
        assertEquals(SpeakerRole.INTERVIEWER, t.segments().get(0).speaker());
        assertEquals("block1", t.segments().get(0).blockId());

        // ответ кандидата
        assertEquals(SpeakerRole.CANDIDATE, t.segments().get(1).speaker());
        assertEquals("block1", t.segments().get(1).blockId());
        assertTrue(t.segments().get(1).text().contains("шесть лет"));

        // переход в block2
        assertEquals("block2", t.segments().get(2).blockId());
        assertEquals("block2", t.segments().get(3).blockId());

        // таймстемпы должны прокидываться
        assertEquals(2000L, t.segments().get(0).startMs());
        assertEquals(30000L, t.segments().get(3).endMs());
    }

    @Test
    void пустоеМедиаДаётПустойTranscript() {
        Transcriber empty = (m, c) -> List.of();
        TranscriptionPipeline pipeline = new TranscriptionPipeline(
                empty, roleAssigner, blockClassifier, rubricLoader);
        Transcript t = pipeline.transcribe(new byte[]{}, "audio/mpeg", "senior-go-developer");
        assertTrue(t.segments().isEmpty());
    }
}
