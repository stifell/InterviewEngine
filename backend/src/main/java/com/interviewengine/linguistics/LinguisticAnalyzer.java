package com.interviewengine.linguistics;

/**
 * Извлекает комп-лингвистические признаки из ответа кандидата.
 * <p>Это сменный компонент: v1 — правила на Java, v2 — Python-сайдкар
 * с Natasha / spaCy-ru / pymorphy3.
 */
public interface LinguisticAnalyzer {

    LinguisticFeatures analyze(String answer);
}
