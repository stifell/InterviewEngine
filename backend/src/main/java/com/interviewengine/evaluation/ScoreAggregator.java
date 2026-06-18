package com.interviewengine.evaluation;

import com.interviewengine.domain.Competency;
import com.interviewengine.domain.CompetencyEvaluation;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.Recommendation;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.Scorecard;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Шаг 7 конвейера: агрегация баллов индикаторов в компетенции и в итоговую рекомендацию.
 *
 * <p>Правила (§8 CLAUDE.md):
 * <ul>
 *   <li>Балл компетенции — среднее баллов её индикаторов (для v1 без индивидуальных
 *       весов индикаторов; они появятся в вехе 5 при необходимости).</li>
 *   <li>{@code acceptable} компетенции = все индикаторы прошли свой acceptableFrom.</li>
 *   <li>Итоговая рекомендация:
 *     <ul>
 *       <li>{@code NO_HIRE} — если хоть одна компетенция не acceptable
 *           (в v1 все компетенции считаются критичными).</li>
 *       <li>{@code STRONG_HIRE} — все acceptable И overall ≥ 4.0.</li>
 *       <li>{@code HIRE} — все acceptable, но overall &lt; 4.0.</li>
 *     </ul></li>
 *   <li>{@code overallScore} — взвешенное среднее баллов компетенций по их рубрикаторным весам.</li>
 * </ul>
 */
@Service
public class ScoreAggregator {

    public Scorecard aggregate(Rubric rubric, Map<String, IndicatorEvaluation> evaluationsByIndicatorId) {
        List<CompetencyEvaluation> competencyEvals = new ArrayList<>();

        for (Competency competency : rubric.competencies()) {
            // Компетенцию, у которой ни один индикатор не был реально оценён, в скоркарту
            // не включаем. Это нужно для просодического домена: на текстовом интервью
            // (нет аудио) его индикаторы пропущены, и иначе он давал бы 0/неприемлемо и
            // тянул весь результат в NO_HIRE по характеристикам, которых физически нет.
            boolean anyEvaluated = competency.indicators().stream()
                    .anyMatch(i -> evaluationsByIndicatorId.containsKey(i.id()));
            if (!anyEvaluated) {
                continue;
            }

            List<IndicatorEvaluation> indicatorEvals = new ArrayList<>();
            for (Indicator indicator : competency.indicators()) {
                IndicatorEvaluation ie = evaluationsByIndicatorId.get(indicator.id());
                if (ie == null) {
                    // судья не оценил этот индикатор — фиксируем как 0/неприемлемо с пояснением,
                    // чтобы итог был воспроизводимым, а не падал в NPE
                    ie = new IndicatorEvaluation(
                            indicator.id(), 0, false, "",
                            "Индикатор не был оценён (нет соответствующего ответа в транскрипте)."
                    );
                }
                indicatorEvals.add(ie);
            }

            double avg = indicatorEvals.stream().mapToInt(IndicatorEvaluation::score).average().orElse(0.0);
            boolean acceptable = !indicatorEvals.isEmpty()
                    && indicatorEvals.stream().allMatch(IndicatorEvaluation::acceptable);

            competencyEvals.add(new CompetencyEvaluation(
                    competency.id(),
                    competency.title(),
                    competency.weight(),
                    avg,
                    acceptable,
                    indicatorEvals
            ));
        }

        double overall = weightedAverage(competencyEvals);
        Recommendation recommendation = decide(competencyEvals, overall);

        return new Scorecard(rubric.position(), overall, recommendation, competencyEvals);
    }

    private static double weightedAverage(List<CompetencyEvaluation> evals) {
        double weighted = 0.0;
        double totalWeight = 0.0;
        for (CompetencyEvaluation c : evals) {
            weighted += c.score() * c.weight();
            totalWeight += c.weight();
        }
        return totalWeight == 0.0 ? 0.0 : weighted / totalWeight;
    }

    private static Recommendation decide(List<CompetencyEvaluation> evals, double overall) {
        boolean allAcceptable = !evals.isEmpty() && evals.stream().allMatch(CompetencyEvaluation::acceptable);
        if (!allAcceptable) {
            return Recommendation.NO_HIRE;
        }
        return overall >= 4.0 ? Recommendation.STRONG_HIRE : Recommendation.HIRE;
    }
}
