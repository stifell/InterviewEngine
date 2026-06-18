package com.interviewengine.domain;

import java.util.List;

/**
 * Итоговый результат конвейера: скоркарта кандидата + оценка интервьюера.
 * Это то, что отдаётся клиенту в API {@code GET /api/interviews/{id}/result}.
 *
 * @param answerFeatures извлечённые характеристики ответов (лексика + просодика) с
 *                       привязкой к индикаторам/доменам — для демонстрации связи
 *                       «характеристика → система оценки» (требования 1 и 3)
 */
public record EvaluationResult(
        String position,
        Scorecard scorecard,
        InterviewerEvaluation interviewerEvaluation,
        List<AnswerFeatureView> answerFeatures
) {
    public EvaluationResult {
        answerFeatures = answerFeatures == null ? List.of() : List.copyOf(answerFeatures);
    }

    /** Совместимый конструктор без признаков (используется там, где они не собираются). */
    public EvaluationResult(String position, Scorecard scorecard, InterviewerEvaluation interviewerEvaluation) {
        this(position, scorecard, interviewerEvaluation, List.of());
    }
}
