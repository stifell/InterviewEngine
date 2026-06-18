package com.interviewengine.domain;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Детерминированные тесты агрегации просодики (без аудио и без LLM, §11 CLAUDE.md).
 */
class ProsodicFeaturesTest {

    private static ProsodicFeatures of(double speechRate, int pauseCount, double meanPauseMs,
                                       double pitchVar, long durationMs) {
        return new ProsodicFeatures(
                speechRate, speechRate, 0.2, pauseCount, meanPauseMs,
                150.0, pitchVar, 60.0, 5.0, 0.8, durationMs);
    }

    @Test
    void пустойИлиNullСписокДаётNull() {
        assertNull(ProsodicFeatures.aggregate(null));
        assertNull(ProsodicFeatures.aggregate(List.of()));
    }

    @Test
    void списокИзОдногоNullИНулевойДлительностиДаётNull() {
        assertNull(ProsodicFeatures.aggregate(Arrays.asList((ProsodicFeatures) null)));
        assertNull(ProsodicFeatures.aggregate(List.of(of(100, 0, 0, 1.0, 0))));
    }

    @Test
    void непрерывныеМетрикиУсредняютсяВзвешенноПоДлительности() {
        // segment1: speechRate=100, duration=1000; segment2: speechRate=200, duration=3000
        // weighted = (100*1000 + 200*3000) / 4000 = 175
        ProsodicFeatures agg = ProsodicFeatures.aggregate(List.of(
                of(100, 0, 0, 2.0, 1000),
                of(200, 0, 0, 6.0, 3000)
        ));
        assertEquals(175.0, agg.speechRateWpm(), 0.001);
        // pitchVar: (2*1000 + 6*3000)/4000 = 5.0
        assertEquals(5.0, agg.pitchVariationSemitones(), 0.001);
        assertEquals(4000L, agg.durationMs());
    }

    @Test
    void паузыСуммируютсяАСредняяПаузаВзвешенаПоЧислуПауз() {
        // segment1: 2 паузы по 100мс; segment2: 4 паузы по 400мс
        // pauseCount = 6; meanPause = (100*2 + 400*4)/6 = (200+1600)/6 = 300
        ProsodicFeatures agg = ProsodicFeatures.aggregate(List.of(
                of(100, 2, 100, 1.0, 1000),
                of(100, 4, 400, 1.0, 1000)
        ));
        assertEquals(6, agg.pauseCount());
        assertEquals(300.0, agg.meanPauseMs(), 0.001);
    }

    @Test
    void nullИНулевыеЭлементыИгнорируютсяПриАгрегации() {
        ProsodicFeatures agg = ProsodicFeatures.aggregate(Arrays.asList(
                null,
                of(120, 1, 50, 3.0, 0),     // нулевая длительность — игнор
                of(120, 1, 50, 3.0, 2000)   // единственный валидный
        ));
        assertEquals(120.0, agg.speechRateWpm(), 0.001);
        assertEquals(2000L, agg.durationMs());
    }
}
