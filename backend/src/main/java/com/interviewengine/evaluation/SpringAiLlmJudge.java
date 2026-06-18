package com.interviewengine.evaluation;

import com.interviewengine.config.ModelSelector;
import com.interviewengine.domain.Indicator;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.linguistics.LinguisticFeatures;
import com.interviewengine.linguistics.TermCoverage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Реализация LlmJudge через Spring AI {@link ChatClient}.
 * <p>Промпт лежит в {@code resources/prompts/evaluate-indicator.st}; структурированный
 * вывод — нативно через {@code .entity(IndicatorEvaluation.class)} (§11 CLAUDE.md).
 */
@Service
public class SpringAiLlmJudge implements LlmJudge {

    private static final Logger log = LoggerFactory.getLogger(SpringAiLlmJudge.class);

    /** «Please retry in 58.49s» — парсим seconds из сообщения Gemini 429. */
    private static final Pattern RETRY_AFTER_PATTERN =
            Pattern.compile("retry in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);

    private static final int MAX_ATTEMPTS = 4;
    /** Добавляем 3 сек поверх Retry-After на случай clock skew. */
    private static final long RETRY_BUFFER_MS = 3_000;
    /** Дефолт, если парсинг не удался, но ошибка — 429. */
    private static final long DEFAULT_RETRY_MS = 65_000;
    /** Бэкофф при 503 / перегрузке модели — обычно проходит быстрее, чем окно 429. */
    private static final long SERVER_BUSY_RETRY_MS = 15_000;

    private final ChatClient chatClient;
    private final Resource promptTemplate;
    private final ModelSelector modelSelector;

    public SpringAiLlmJudge(
            ChatClient.Builder chatClientBuilder,
            @Value("classpath:prompts/evaluate-indicator.st") Resource promptTemplate,
            ModelSelector modelSelector
    ) {
        this.chatClient = chatClientBuilder.build();
        this.promptTemplate = promptTemplate;
        this.modelSelector = modelSelector;
    }

    @Override
    public IndicatorEvaluation evaluate(Indicator indicator, String answer,
                                        LinguisticFeatures features, ProsodicFeatures prosody) {
        RuntimeException lastException = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return doEvaluate(indicator, answer, features, prosody);
            } catch (RuntimeException e) {
                long waitMs = extractRetryAfterMs(e);
                if (waitMs < 0) {
                    throw e; // не временная ошибка (429/503) — пробрасываем сразу
                }
                lastException = e;
                if (attempt == MAX_ATTEMPTS) break;
                log.warn("Gemini недоступен (429/503) на индикаторе '{}'; жду {}мс (попытка {}/{})",
                        indicator.id(), waitMs, attempt, MAX_ATTEMPTS);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw lastException;
    }

    private IndicatorEvaluation doEvaluate(Indicator indicator, String answer,
                                           LinguisticFeatures features, ProsodicFeatures prosody) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("indicatorId", indicator.id());
        params.put("indicatorText", indicator.text());
        params.put("barsBlock", formatBars(indicator.bars()));
        params.put("acceptableFrom", indicator.acceptableFrom());
        params.put("featuresBlock", formatFeaturesWithCoverage(features, indicator, answer));
        params.put("prosodyBlock", formatProsody(prosody));
        params.put("answer", answer == null ? "" : answer);

        String model = modelSelector.getModel();
        log.debug("LLM-вызов: модель={}, индикатор={}", model, indicator.id());
        return chatClient.prompt()
                .options(GoogleGenAiChatOptions.builder().model(model).build())
                .user(u -> u.text(promptTemplate).params(params))
                .call()
                .entity(IndicatorEvaluation.class);
    }

    /**
     * Ищет в цепочке исключений признаки временной ошибки Gemini, которую стоит переждать:
     * 429 (rate limit, с «retry in Xs») и 503 (перегрузка модели / «high demand»).
     * Возвращает миллисекунды ожидания, или {@code -1} если ошибка не временная.
     */
    private static long extractRetryAfterMs(Throwable e) {
        Throwable cause = e;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                Matcher m = RETRY_AFTER_PATTERN.matcher(msg);
                if (m.find()) {
                    long seconds = (long) Math.ceil(Double.parseDouble(m.group(1)));
                    return seconds * 1000 + RETRY_BUFFER_MS;
                }
                if (msg.contains("429")) {
                    return DEFAULT_RETRY_MS;
                }
                String lower = msg.toLowerCase(java.util.Locale.ROOT);
                if (msg.contains("503") || lower.contains("overloaded")
                        || lower.contains("high demand") || lower.contains("unavailable")
                        || lower.contains("try again later")) {
                    return SERVER_BUSY_RETRY_MS;
                }
            }
            cause = cause.getCause();
        }
        return -1;
    }

    /**
     * Преобразует BARS-карту {уровень → описание} в человекочитаемый блок,
     * отсортированный по уровням по возрастанию.
     */
    static String formatBars(Map<Integer, String> bars) {
        if (bars == null || bars.isEmpty()) {
            return "(BARS не задан)";
        }
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(bars).forEach((level, desc) ->
                sb.append("  ").append(level).append(": ").append(desc).append('\n'));
        return sb.toString().stripTrailing();
    }

    /**
     * Сериализует лингвистические признаки в блок и дописывает {@code termCoverage},
     * если у индикатора задан список доменных терминов.
     */
    private static String formatFeaturesWithCoverage(
            LinguisticFeatures f, Indicator indicator, String answer) {
        String base = formatFeatures(f);
        if (indicator.terms().isEmpty()) {
            return base;
        }
        double tc = TermCoverage.compute(answer, indicator.terms());
        return base + "\n  termCoverage: " + round(tc)
                + " (" + countMatched(answer, indicator.terms())
                + "/" + indicator.terms().size() + " терминов)";
    }

    private static int countMatched(String answer, java.util.List<String> terms) {
        if (answer == null || answer.isBlank()) return 0;
        return (int) terms.stream()
                .filter(t -> t != null && !t.isBlank())
                .filter(t -> TermCoverage.compute(answer, java.util.List.of(t)) > 0.0)
                .count();
    }

    /**
     * Сериализует значимые лингвистические признаки в компактный читаемый блок —
     * этот блок LLM использует как объективное доказательство наряду с цитатой.
     */
    static String formatFeatures(LinguisticFeatures f) {
        if (f == null) {
            return "(признаки не извлечены)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  answerLength: ").append(f.answerLength()).append(" слов\n");
        sb.append("  firstPersonSingularCount: ").append(f.firstPersonSingularCount()).append('\n');
        sb.append("  firstPersonPluralCount: ").append(f.firstPersonPluralCount()).append('\n');
        sb.append("  ownershipRatio: ").append(round(f.ownershipRatio()))
                .append(f.hasFirstPerson() ? "" : " (1-го лица в ответе нет)").append('\n');
        sb.append("  fillerDensity: ").append(round(f.fillerDensity())).append(" /100 слов\n");
        sb.append("  hedgingDensity: ").append(round(f.hedgingDensity())).append(" /100 слов\n");
        sb.append("  hasMetrics: ").append(f.hasMetrics()).append('\n');
        sb.append("  lexicalDiversity (TTR): ").append(round(f.lexicalDiversity()));
        return sb.toString();
    }

    /**
     * Сериализует просодические признаки речевого сигнала в читаемый блок. Если аудио
     * не было (текстовый транскрипт / Gemini), возвращает явный маркер недоступности —
     * чтобы LLM не штрафовала за отсутствие данных, которых физически нет.
     */
    static String formatProsody(ProsodicFeatures p) {
        if (p == null) {
            return "(аудио недоступно — просодика не извлекалась; не учитывай её при оценке)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  speechRate: ").append(round(p.speechRateWpm())).append(" слов/мин\n");
        sb.append("  articulationRate: ").append(round(p.articulationRateWpm())).append(" слов/мин (без пауз)\n");
        sb.append("  pauseRatio: ").append(round(p.pauseRatio())).append(" (доля пауз)\n");
        sb.append("  pauseCount: ").append(p.pauseCount()).append('\n');
        sb.append("  meanPauseMs: ").append(round(p.meanPauseMs())).append(" мс\n");
        sb.append("  pitchMeanHz: ").append(round(p.pitchMeanHz())).append(" Гц\n");
        sb.append("  pitchVariationSemitones: ").append(round(p.pitchVariationSemitones()))
                .append(" пт (выразительность интонации)\n");
        sb.append("  intensityMeanDb: ").append(round(p.intensityMeanDb())).append(" дБ\n");
        sb.append("  intensityVariationDb: ").append(round(p.intensityVariationDb())).append(" дБ\n");
        sb.append("  voicedRatio: ").append(round(p.voicedRatio()));
        return sb.toString();
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }
}
