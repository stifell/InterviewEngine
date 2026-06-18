package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;
import com.interviewengine.rubric.RubricLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeuristicBlockClassifierTest {

    private final HeuristicBlockClassifier classifier = new HeuristicBlockClassifier();
    private final Rubric rubric = new RubricLoader().loadByPosition("senior-go-developer");

    @Test
    void прелюдияНеПопадаетНиВКакойБлок() {
        List<RawSpeakerSegment> raw = List.of(
                new RawSpeakerSegment("spk0", 0, 2000, "Здравствуйте, рад знакомству"),
                new RawSpeakerSegment("spk1", 2000, 4000, "Здравствуйте, я тоже")
        );
        List<String> blocks = classifier.classify(raw, rubric);
        assertNull(blocks.get(0));
        assertNull(blocks.get(1));
    }

    @Test
    void открывающаяРепликаПереключаетБлок() {
        List<RawSpeakerSegment> raw = List.of(
                new RawSpeakerSegment("spk0", 0, 4000,
                        "Расскажите коротко о вашем опыте коммерческой разработки на Go: какие проекты решали"),
                new RawSpeakerSegment("spk1", 4000, 10000, "Я работаю на Go шесть лет."),
                new RawSpeakerSegment("spk0", 10000, 15000,
                        "Расскажите о самой сложной технической проблеме за последние полгода"),
                new RawSpeakerSegment("spk1", 15000, 20000, "Я переписал критичный обработчик заказов"),
                new RawSpeakerSegment("spk0", 20000, 25000,
                        "Расскажите как устроена работа горутин и каналов в Go"),
                new RawSpeakerSegment("spk1", 25000, 30000, "Я использую буферизованные каналы в пайплайнах")
        );

        List<String> blocks = classifier.classify(raw, rubric);

        assertEquals("block1", blocks.get(0));
        assertEquals("block1", blocks.get(1));
        assertEquals("block2", blocks.get(2));
        assertEquals("block2", blocks.get(3));
        assertEquals("block3", blocks.get(4));
        assertEquals("block3", blocks.get(5));
    }
}
