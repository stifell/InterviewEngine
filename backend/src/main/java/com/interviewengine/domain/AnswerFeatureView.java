package com.interviewengine.domain;

import com.interviewengine.linguistics.LinguisticFeatures;

import java.util.List;

/**
 * Снимок извлечённых характеристик одного ответа кандидата с привязкой к индикатору.
 * Возвращается в {@link EvaluationResult#answerFeatures()} специально для демонстрации
 * (требования 1 и 3): UI рисует таблицу «характеристика → индикатор → домен», показывая,
 * как лексические и просодические значения соотносятся с системой оценки.
 *
 * @param indicatorId     индикатор, при оценке которого использовались эти признаки
 * @param competencyId    компетенция (домен) индикатора
 * @param competencyTitle человекочитаемое название домена
 * @param answerExcerpt   начало ответа кандидата (для контекста в UI)
 * @param lexical         лексические (комп-лингвистические) признаки ответа, R4
 * @param prosody         просодические признаки ответа; {@code null}, если аудио не было
 * @param signals         сигналы индикатора из рубрикатора (на какие признаки он опирается)
 */
public record AnswerFeatureView(
        String indicatorId,
        String competencyId,
        String competencyTitle,
        String answerExcerpt,
        LinguisticFeatures lexical,
        ProsodicFeatures prosody,
        List<String> signals
) {
    public AnswerFeatureView {
        signals = signals == null ? List.of() : List.copyOf(signals);
    }
}
