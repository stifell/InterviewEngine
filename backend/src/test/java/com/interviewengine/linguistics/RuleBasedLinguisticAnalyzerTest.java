package com.interviewengine.linguistics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedLinguisticAnalyzerTest {

    private final RuleBasedLinguisticAnalyzer analyzer = new RuleBasedLinguisticAnalyzer();

    @Test
    void пустойОтветДаётНулевыеПризнаки() {
        LinguisticFeatures f = analyzer.analyze("");
        assertEquals(0, f.answerLength());
        assertFalse(f.hasFirstPerson());
        assertFalse(f.hasMetrics());
        assertEquals(0.0, f.ownershipRatio());
        assertEquals(0.0, f.lexicalDiversity());
    }

    @Test
    void личныйРассказСМетрикамиДаётВысокийOwnershipИHasMetrics() {
        String answer = """
                Я лично переписал критичный сервис с нуля. Я провёл нагрузочные тесты
                и сократил латентность p99 с 200 мс до 50 мс. Я выкатил это в прод за неделю.
                """;
        LinguisticFeatures f = analyzer.analyze(answer);

        assertTrue(f.hasFirstPerson(), "должно быть 1-е лицо");
        assertTrue(f.ownershipRatio() > 0.9,
                "ownershipRatio должен быть высоким, получено " + f.ownershipRatio());
        assertTrue(f.hasMetrics(), "метрики должны быть распознаны");
        assertEquals(0.0, f.fillerDensity());
        assertEquals(0.0, f.hedgingDensity());
    }

    @Test
    void обезличенныйОтветБезPersonalPronounsДаётOwnershipRatioВноль() {
        String answer = "Команда оптимизировала запросы и переписала кеш на новой технологии.";
        LinguisticFeatures f = analyzer.analyze(answer);

        assertFalse(f.hasFirstPerson());
        assertEquals(0.0, f.ownershipRatio());
        assertFalse(f.hasMetrics());
    }

    @Test
    void ответКомандыДаётНизкийOwnershipRatio() {
        String answer = "Мы вместе обсуждали задачу. Нам пришлось разделить работу. Наш тимлид помог.";
        LinguisticFeatures f = analyzer.analyze(answer);

        assertTrue(f.hasFirstPerson());
        assertEquals(0, f.firstPersonSingularCount());
        assertTrue(f.firstPersonPluralCount() >= 3);
        assertEquals(0.0, f.ownershipRatio(), 0.0001);
    }

    @Test
    void смешанныйОтветДаётПромежуточныйOwnership() {
        String answer = "Сначала мы обсудили задачу. Потом я взял её на себя и я довёл её до конца.";
        LinguisticFeatures f = analyzer.analyze(answer);

        assertTrue(f.ownershipRatio() > 0.5 && f.ownershipRatio() < 1.0,
                "ожидался промежуточный ownership, получено " + f.ownershipRatio());
    }

    @Test
    void словаПаразитыПовышаютFillerDensity() {
        String answer = "Ну вот мы там как бы делали, в общем, типа сервис, короче.";
        LinguisticFeatures f = analyzer.analyze(answer);

        assertTrue(f.fillerDensity() > 30.0,
                "должна быть высокая плотность слов-паразитов, получено " + f.fillerDensity());
    }

    @Test
    void хеджингСловаПовышаютHedgingDensity() {
        String answer = "Наверное, это вроде работает. Кажется, всё нормально, скорее всего.";
        LinguisticFeatures f = analyzer.analyze(answer);

        assertTrue(f.hedgingDensity() > 20.0,
                "ожидалась высокая hedgingDensity, получено " + f.hedgingDensity());
    }

    @Test
    void hasMetricsРаспознаётПроцентыИЕдиницы() {
        assertTrue(analyzer.analyze("Ускорил на 30%").hasMetrics());
        assertTrue(analyzer.analyze("Поднял с 5 тыс до 50 тыс RPS").hasMetrics());
        assertTrue(analyzer.analyze("Сократил с 200 мс до 50 мс").hasMetrics());
        assertTrue(analyzer.analyze("100 -> 200").hasMetrics());
        assertTrue(analyzer.analyze("100 → 200").hasMetrics());
        assertFalse(analyzer.analyze("Стало быстрее, заметно лучше").hasMetrics());
    }

    @Test
    void lexicalDiversityМеньшеНаПовторах() {
        String repetitive = "так так так так так";
        String diverse = "контекст задача действие результат вывод";

        double repTtr = analyzer.analyze(repetitive).lexicalDiversity();
        double divTtr = analyzer.analyze(diverse).lexicalDiversity();

        assertTrue(divTtr > repTtr);
        assertEquals(1.0, divTtr, 0.0001);
        assertEquals(0.2, repTtr, 0.0001);
    }

    @Test
    void answerLengthСчитаетСлова() {
        LinguisticFeatures f = analyzer.analyze("Раз два три четыре пять.");
        assertEquals(5, f.answerLength());
    }
}
