package com.interviewengine.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.interviewengine.domain.EvaluationResult;
import com.interviewengine.domain.IndicatorEvaluation;
import com.interviewengine.domain.InterviewStatus;
import com.interviewengine.domain.Transcript;
import com.interviewengine.evaluation.EvaluationPipeline;
import com.interviewengine.persistence.InterviewEntity;
import com.interviewengine.persistence.InterviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Асинхронный исполнитель оценочного конвейера для одного интервью.
 * Вынесен в отдельный бин, чтобы корректно работал прокси {@code @Async}:
 * вызов self-method из {@code InterviewService} не прошёл бы через прокси.
 *
 * <p>Реализует §13 CLAUDE.md: кэширует результаты LLM по (интервью, индикатор).
 * При каждом запуске загружает существующий кэш ({@code eval_cache_json}), передаёт
 * его в {@link EvaluationPipeline} — тот пропускает LLM для уже оценённых индикаторов —
 * и сохраняет обновлённый кэш обратно в БД.
 */
@Component
public class AsyncEvaluator {

    private static final Logger log = LoggerFactory.getLogger(AsyncEvaluator.class);

    private static final TypeReference<Map<String, IndicatorEvaluation>> CACHE_TYPE =
            new TypeReference<>() {};

    private final InterviewRepository repository;
    private final EvaluationPipeline pipeline;
    private final JsonCodec json;

    public AsyncEvaluator(InterviewRepository repository, EvaluationPipeline pipeline, JsonCodec json) {
        this.repository = repository;
        this.pipeline = pipeline;
        this.json = json;
    }

    @Async
    @Transactional
    public void run(UUID interviewId) {
        InterviewEntity entity = repository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));

        entity.setStatus(InterviewStatus.RUNNING);
        entity.setErrorMessage(null);
        repository.saveAndFlush(entity);

        try {
            Transcript transcript = json.fromJson(entity.getTranscriptJson(), Transcript.class);

            // Загружаем ранее накопленный кэш оценок индикаторов (§13 CLAUDE.md).
            // При первом запуске evalCacheJson == null → используем пустую карту.
            Map<String, IndicatorEvaluation> existingCache = loadCache(entity);
            log.debug("Интервью {}: загружен кэш на {} индикаторов", interviewId, existingCache.size());

            // Коллбэк сохраняет частичный кэш в БД после каждого успешного LLM-вызова.
            // При 429 или другой ошибке уже оценённые индикаторы не теряются.
            EvaluationResult result = pipeline.evaluate(
                    entity.getPosition(), transcript, existingCache,
                    partial -> {
                        entity.setEvalCacheJson(json.toJson(partial));
                        repository.saveAndFlush(entity);
                        log.debug("Частичный кэш сохранён: {} индикаторов", partial.size());
                    });

            // Сохраняем обновлённый кэш: объединяем старый с тем, что пришло из pipeline.
            Map<String, IndicatorEvaluation> updatedCache = mergeCache(existingCache, result);
            entity.setEvalCacheJson(json.toJson(updatedCache));

            entity.setResultJson(json.toJson(result));
            entity.setStatus(InterviewStatus.DONE);
            repository.save(entity);
            log.info("Оценка интервью {} завершена, кэш сохранён ({} индикаторов)",
                    interviewId, updatedCache.size());
        } catch (RuntimeException e) {
            log.error("Оценка интервью {} упала", interviewId, e);
            entity.setStatus(InterviewStatus.FAILED);
            entity.setErrorMessage(e.getMessage());
            repository.save(entity);
        }
    }

    // -------------------------------------------------------------------------

    /** Десериализует {@code eval_cache_json} или возвращает пустую карту. */
    private Map<String, IndicatorEvaluation> loadCache(InterviewEntity entity) {
        String cacheJson = entity.getEvalCacheJson();
        if (cacheJson == null || cacheJson.isBlank()) {
            return Map.of();
        }
        return json.fromJson(cacheJson, CACHE_TYPE);
    }

    /**
     * Строит обновлённую карту кэша: берёт всё, что уже было в {@code existingCache},
     * и дополняет/перезаписывает оценками из свежего результата конвейера.
     * Используем LinkedHashMap для воспроизводимого порядка при сериализации.
     */
    private static Map<String, IndicatorEvaluation> mergeCache(
            Map<String, IndicatorEvaluation> existingCache,
            EvaluationResult result
    ) {
        Map<String, IndicatorEvaluation> merged = new LinkedHashMap<>(existingCache);
        result.scorecard().competencies().forEach(ce ->
                ce.indicators().forEach(ie -> merged.put(ie.indicatorId(), ie))
        );
        return merged;
    }
}
