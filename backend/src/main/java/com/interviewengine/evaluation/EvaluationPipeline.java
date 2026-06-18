package com.interviewengine.evaluation;

import com.interviewengine.domain.AnswerFeatureView;
import com.interviewengine.domain.Competency;
import com.interviewengine.domain.EvaluationResult;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.InterviewerEvaluation;
import com.interviewengine.domain.Probe;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.Scorecard;
import com.interviewengine.domain.Transcript;
import com.interviewengine.linguistics.LinguisticAnalyzer;
import com.interviewengine.linguistics.LinguisticFeatures;
import com.interviewengine.rubric.RubricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Оркестратор шагов 4–9 конвейера (§5 CLAUDE.md). Связывает разбор транскрипта,
 * лингвистический анализ, LLM-судью, агрегацию и оценку интервьюера в один поток.
 *
 * <p>Шаги 1–3 (ingest / роли / разбивка на блоки) считаются выполненными до входа
 * в этот сервис — на вход поступает уже speaker- и block-tagged {@link Transcript}.
 *
 * <p>Источник ответа для каждого индикатора:
 * <ul>
 *   <li>если индикатор связан с пробой (probe.indicator == indicator.id) — берётся
 *       ответ на эту пробу;</li>
 *   <li>иначе — основной ответ блока, к которому привязана компетенция.</li>
 * </ul>
 */
@Service
public class EvaluationPipeline {

    private static final Logger log = LoggerFactory.getLogger(EvaluationPipeline.class);

    /**
     * Сигналы, источник которых — просодика речевого сигнала (а не текст). Индикатор,
     * у которого ВСЕ сигналы из этого набора, оценивается только при наличии аудио;
     * без него (текстовый транскрипт) он пропускается, а его компетенция исключается
     * из скоркарты ({@link ScoreAggregator}). Имена совпадают с полями
     * {@link ProsodicFeatures} / именами в рубрикаторе.
     */
    private static final Set<String> PROSODIC_SIGNALS = Set.of(
            "speechRate", "articulationRate", "pauseRatio", "meanPauseMs", "pauseCount",
            "pitchMeanHz", "pitchVariationSemitones", "intensityMeanDb",
            "intensityVariationDb", "voicedRatio"
    );

    private final RubricLoader rubricLoader;
    private final AnswerAssembler answerAssembler;
    private final LinguisticAnalyzer linguisticAnalyzer;
    private final LlmJudge llmJudge;
    private final ScoreAggregator scoreAggregator;
    private final InterviewerEvaluator interviewerEvaluator;

    public EvaluationPipeline(
            RubricLoader rubricLoader,
            AnswerAssembler answerAssembler,
            LinguisticAnalyzer linguisticAnalyzer,
            LlmJudge llmJudge,
            ScoreAggregator scoreAggregator,
            InterviewerEvaluator interviewerEvaluator
    ) {
        this.rubricLoader = rubricLoader;
        this.answerAssembler = answerAssembler;
        this.linguisticAnalyzer = linguisticAnalyzer;
        this.llmJudge = llmJudge;
        this.scoreAggregator = scoreAggregator;
        this.interviewerEvaluator = interviewerEvaluator;
    }

    /**
     * Запускает оценочный конвейер без кэша (первый запуск или полный сброс).
     */
    public EvaluationResult evaluate(String position, Transcript transcript) {
        return evaluate(position, transcript, Map.of());
    }

    /**
     * Запускает оценочный конвейер с кэшем ранее вычисленных оценок индикаторов.
     * Если для индикатора в кэше уже есть {@link IndicatorEvaluation}, LLM не вызывается —
     * кэшированный результат переиспользуется напрямую. Это соответствует §13 CLAUDE.md:
     * «кэшируй результаты LLM по (интервью, индикатор)».
     *
     * @param existingCache      ключ = indicator.id(); если пуст — поведение как без кэша
     * @param partialCacheSaver  коллбэк, вызываемый после каждого нового LLM-вызова с
     *                           актуальной картой {@code indicatorId → evaluation}. Это
     *                           позволяет сохранить частичные результаты в БД, чтобы при
     *                           429-ошибке уже оценённые индикаторы не пересчитывались.
     *                           Можно передать {@code null} — тогда сохранения не происходит.
     */
    public EvaluationResult evaluate(String position, Transcript transcript,
                                     Map<String, IndicatorEvaluation> existingCache,
                                     Consumer<Map<String, IndicatorEvaluation>> partialCacheSaver) {
        Rubric rubric = rubricLoader.loadByPosition(position);
        AssembledAnswers assembled = answerAssembler.assemble(transcript, rubric);

        // Карта indicator.id → probe (если индикатор связан с пробой)
        Map<String, Probe> probeByIndicator = new LinkedHashMap<>();
        rubric.blocks().forEach(b -> b.probes().forEach(p ->
                probeByIndicator.put(p.indicator(), p)));

        Map<String, IndicatorEvaluation> evaluationsByIndicatorId = new LinkedHashMap<>(existingCache);
        List<AnswerFeatureView> answerFeatures = new ArrayList<>();

        for (Competency competency : rubric.competencies()) {
            for (Indicator indicator : competency.indicators()) {
                String answer = resolveAnswer(indicator, competency, probeByIndicator, assembled);
                if (answer == null || answer.isBlank()) {
                    // нет ответа — индикатор не оценивается через LLM (§13: нет баллов без доказательства)
                    continue;
                }
                ProsodicFeatures prosody = resolveProsody(indicator, competency, probeByIndicator, assembled);

                // Чисто-просодический индикатор без аудио оценить нечем — пропускаем.
                // Его компетенция будет исключена из скоркарты в ScoreAggregator.
                if (prosody == null && isProsodyDependent(indicator)) {
                    log.debug("Пропускаем чисто-просодический индикатор '{}' — нет аудио", indicator.id());
                    continue;
                }

                LinguisticFeatures features = linguisticAnalyzer.analyze(answer);
                // Снимок признаков для демонстрации связи «характеристика → индикатор → домен»
                answerFeatures.add(new AnswerFeatureView(
                        indicator.id(), competency.id(), competency.title(),
                        excerpt(answer), features, prosody, indicator.signals()));

                // Кэш: LLM вызывается только если для индикатора ещё нет результата (§13)
                if (evaluationsByIndicatorId.containsKey(indicator.id())) {
                    log.debug("Кэш: пропускаем LLM для индикатора '{}'", indicator.id());
                    continue;
                }

                log.debug("LLM оценивает индикатор '{}'", indicator.id());
                IndicatorEvaluation eval = llmJudge.evaluate(indicator, answer, features, prosody);
                evaluationsByIndicatorId.put(indicator.id(), eval);

                // Сохраняем частичный кэш после каждого успешного LLM-вызова.
                // Если следующий вызов упадёт с 429, уже оценённые индикаторы не потеряются.
                if (partialCacheSaver != null) {
                    partialCacheSaver.accept(Map.copyOf(evaluationsByIndicatorId));
                }

                // Пауза между LLM-вызовами: Gemini Free Tier = 20 RPM → ≥3с между запросами.
                // Без паузы 8 индикаторов улетают за <2с и бьют rate limit.
                try {
                    Thread.sleep(3_500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        Scorecard scorecard = scoreAggregator.aggregate(rubric, evaluationsByIndicatorId);
        InterviewerEvaluation interviewerEvaluation = interviewerEvaluator.evaluate(transcript, rubric, assembled);

        return new EvaluationResult(position, scorecard, interviewerEvaluation, answerFeatures);
    }

    /** Индикатор зависит от просодики, если у него есть сигналы и ВСЕ они просодические. */
    private static boolean isProsodyDependent(Indicator indicator) {
        return !indicator.signals().isEmpty()
                && PROSODIC_SIGNALS.containsAll(indicator.signals());
    }

    private static String excerpt(String answer) {
        String trimmed = answer.strip();
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "…";
    }

    /** Устаревший вариант без коллбэка — делегирует с null-сохранителем. */
    public EvaluationResult evaluate(String position, Transcript transcript,
                                     Map<String, IndicatorEvaluation> existingCache) {
        return evaluate(position, transcript, existingCache, null);
    }

    private static String resolveAnswer(
            Indicator indicator,
            Competency competency,
            Map<String, Probe> probeByIndicator,
            AssembledAnswers assembled
    ) {
        Probe probe = probeByIndicator.get(indicator.id());
        if (probe != null) {
            String answer = assembled.probeAnswer(probe.id());
            if (answer != null && !answer.isBlank()) {
                return answer;
            }
            // если пробу не задали — падаем обратно на основной ответ блока компетенции
        }
        return assembled.mainAnswer(competency.block());
    }

    /**
     * Просодика того же ответа, который выбрал {@link #resolveAnswer} — симметрично:
     * сначала проба индикатора, при её отсутствии — основной ответ блока компетенции.
     * Возвращает {@code null}, если аудио не было (текстовый транскрипт).
     */
    private static ProsodicFeatures resolveProsody(
            Indicator indicator,
            Competency competency,
            Map<String, Probe> probeByIndicator,
            AssembledAnswers assembled
    ) {
        Probe probe = probeByIndicator.get(indicator.id());
        if (probe != null) {
            String answer = assembled.probeAnswer(probe.id());
            if (answer != null && !answer.isBlank()) {
                return assembled.probeProsody(probe.id());
            }
        }
        return assembled.mainProsody(competency.block());
    }
}
