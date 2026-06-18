package com.interviewengine.linguistics;

/**
 * Результат комп-лингвистического анализа одного ответа кандидата (R4).
 * <p>Все плотности — на 100 слов. {@code ownershipRatio} — доля 1-го лица
 * единственного числа среди всех маркеров 1-го лица (sg + pl); если ни sg, ни pl
 * не встречаются, значение — 0.0 и {@code hasFirstPerson} = false.
 */
public record LinguisticFeatures(
        int answerLength,
        int firstPersonSingularCount,
        int firstPersonPluralCount,
        boolean hasFirstPerson,
        double ownershipRatio,
        double fillerDensity,
        double hedgingDensity,
        boolean hasMetrics,
        double lexicalDiversity
) {
}
