package com.interviewengine.rubric;

import com.interviewengine.domain.Competency;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.Probe;
import com.interviewengine.domain.Rubric;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RubricLoaderTest {

    private final RubricLoader loader = new RubricLoader();

    @Test
    void загружаетРубрикаторSeniorGo() {
        Rubric rubric = loader.loadByPosition("senior-go-developer");

        assertEquals("Senior Go Developer", rubric.position());
        assertEquals(20, rubric.durationMinutes());
        assertEquals(4, rubric.blocks().size());

        InterviewBlock block2 = rubric.blocks().stream()
                .filter(b -> b.id().equals("block2"))
                .findFirst()
                .orElseThrow();
        assertEquals(3, block2.probes().size());

        double weightSum = rubric.competencies().stream().mapToDouble(Competency::weight).sum();
        assertEquals(1.0, weightSum, 0.0001);

        Indicator firstPerson = findIndicator(rubric, "ind_first_person");
        assertTrue(firstPerson.bars().containsKey(0));
        assertTrue(firstPerson.bars().containsKey(5));
        assertEquals(3, firstPerson.acceptableFrom());
        assertTrue(firstPerson.signals().contains("ownershipRatio"));
    }

    @Test
    void каждаяПробаПривязанаКСуществующемуИндикатору() {
        Rubric rubric = loader.loadByPosition("senior-go-developer");
        Set<String> indicatorIds = rubric.competencies().stream()
                .flatMap(c -> c.indicators().stream())
                .map(Indicator::id)
                .collect(java.util.stream.Collectors.toSet());

        for (InterviewBlock block : rubric.blocks()) {
            for (Probe probe : block.probes()) {
                assertTrue(indicatorIds.contains(probe.indicator()),
                        "проба " + probe.id() + " ссылается на неизвестный indicator " + probe.indicator());
            }
        }
    }

    @Test
    void бросаетОшибкуЕслиРубрикаторНеНайден() {
        RubricLoadException ex = assertThrows(RubricLoadException.class,
                () -> loader.loadByPosition("несуществующая-позиция"));
        assertTrue(ex.getMessage().contains("не найден"));
    }

    @Test
    void валидируетСуммуВесов() {
        String yaml = """
                position: "Test"
                durationMinutes: 10
                blocks:
                  - id: b1
                    order: 1
                    title: "B"
                    timingMinutes: 10
                    mainQuestion: "Q"
                    probes: []
                competencies:
                  - id: c1
                    title: "C1"
                    block: b1
                    weight: 0.4
                    indicators:
                      - id: i1
                        text: "T"
                        signals: []
                        bars: {0: "low", 5: "high"}
                        acceptableFrom: 3
                """;

        RubricLoadException ex = assertThrows(RubricLoadException.class,
                () -> loader.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "inline"));
        assertTrue(ex.getMessage().contains("Сумма весов"));
    }

    @Test
    void отлавливаетСсылкуНаНеизвестныйИндикатор() {
        String yaml = """
                position: "Test"
                durationMinutes: 10
                blocks:
                  - id: b1
                    order: 1
                    title: "B"
                    timingMinutes: 10
                    mainQuestion: "Q"
                    probes:
                      - id: p1
                        text: "?"
                        indicator: i_unknown
                competencies:
                  - id: c1
                    title: "C1"
                    block: b1
                    weight: 1.0
                    indicators:
                      - id: i1
                        text: "T"
                        signals: []
                        bars: {0: "low", 5: "high"}
                        acceptableFrom: 3
                """;

        RubricLoadException ex = assertThrows(RubricLoadException.class,
                () -> loader.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "inline"));
        assertTrue(ex.getMessage().contains("i_unknown"));
    }

    @Test
    void отлавливаетДубликатIdИндикатора() {
        String yaml = """
                position: "Test"
                durationMinutes: 10
                blocks:
                  - id: b1
                    order: 1
                    title: "B"
                    timingMinutes: 10
                    mainQuestion: "Q"
                    probes: []
                competencies:
                  - id: c1
                    title: "C1"
                    block: b1
                    weight: 1.0
                    indicators:
                      - id: i_dup
                        text: "T1"
                        signals: []
                        bars: {0: "low", 5: "high"}
                        acceptableFrom: 3
                      - id: i_dup
                        text: "T2"
                        signals: []
                        bars: {0: "low", 5: "high"}
                        acceptableFrom: 3
                """;

        RubricLoadException ex = assertThrows(RubricLoadException.class,
                () -> loader.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "inline"));
        assertTrue(ex.getMessage().contains("indicator.id повторяется"));
    }

    @Test
    void требуетУровни0И5ВBars() {
        String yaml = """
                position: "Test"
                durationMinutes: 10
                blocks:
                  - id: b1
                    order: 1
                    title: "B"
                    timingMinutes: 10
                    mainQuestion: "Q"
                    probes: []
                competencies:
                  - id: c1
                    title: "C1"
                    block: b1
                    weight: 1.0
                    indicators:
                      - id: i1
                        text: "T"
                        signals: []
                        bars: {2: "mid"}
                        acceptableFrom: 3
                """;

        RubricLoadException ex = assertThrows(RubricLoadException.class,
                () -> loader.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "inline"));
        assertTrue(ex.getMessage().contains("уровни 0 и 5"));
    }

    private static Indicator findIndicator(Rubric rubric, String id) {
        return rubric.competencies().stream()
                .flatMap(c -> c.indicators().stream())
                .filter(i -> i.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
