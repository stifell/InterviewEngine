package com.interviewengine.domain;

/**
 * Результат оценки одного индикатора LLM-судьёй (шаг 6 конвейера, §5 CLAUDE.md).
 *
 * @param indicatorId    id индикатора из рубрикатора (должен совпадать с входным)
 * @param score          балл 0..5 по шкале BARS этого индикатора
 * @param acceptable     прошёл ли индикатор порог acceptableFrom
 * @param evidenceQuote  дословная цитата из ответа кандидата — доказательство балла;
 *                       пустая строка, если LLM не нашёл подходящей цитаты
 * @param rationale      короткое объяснение выбора уровня — со ссылкой на цитату и признаки
 */
public record IndicatorEvaluation(
        String indicatorId,
        int score,
        boolean acceptable,
        String evidenceQuote,
        String rationale
) {
}
