package com.interviewengine.evaluation;

import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.linguistics.LinguisticFeatures;

/**
 * Единая точка доступа к LLM для оценки одного индикатора (§11 CLAUDE.md).
 * <p>Реализация подменяется через конфиг — Gemini / Groq / Ollama скрыты за этим интерфейсом.
 * В пайплайн-тестах должен мокаться.
 *
 * @param features лексические (комп-лингвистические) признаки ответа
 * @param prosody  просодические признаки ответа; {@code null}, если аудио не было
 */
public interface LlmJudge {

    IndicatorEvaluation evaluate(Indicator indicator, String answer,
                                 LinguisticFeatures features, ProsodicFeatures prosody);
}
