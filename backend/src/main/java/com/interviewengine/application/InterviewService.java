package com.interviewengine.application;

import com.interviewengine.domain.EvaluationResult;
import com.interviewengine.domain.InterviewStatus;
import com.interviewengine.domain.Transcript;
import com.interviewengine.persistence.InterviewEntity;
import com.interviewengine.persistence.InterviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Фасад «жизненного цикла интервью»: создание, чтение, запуск оценки, чтение результата.
 *
 * <p>Запуск оценки — асинхронный (§9 «async; возвращает id задачи»). Идентификатор задачи —
 * это id интервью; статус читается через {@link #findById}.
 *
 * <p>Возврат к LLM не повторяется на каждый запрос: если интервью уже {@code DONE},
 * повторный запуск оценки перезапишет результат — это даёт безопасный возврат к источнику
 * (§13 кэширование).
 */
@Service
public class InterviewService {

    private final InterviewRepository repository;
    private final AsyncEvaluator asyncEvaluator;
    private final AsyncTranscriber asyncTranscriber;
    private final JsonCodec json;

    public InterviewService(InterviewRepository repository, AsyncEvaluator asyncEvaluator,
                            AsyncTranscriber asyncTranscriber, JsonCodec json) {
        this.repository = repository;
        this.asyncEvaluator = asyncEvaluator;
        this.asyncTranscriber = asyncTranscriber;
        this.json = json;
    }

    @Transactional
    public UUID create(String position, Transcript transcript) {
        InterviewEntity entity = new InterviewEntity();
        entity.setId(UUID.randomUUID());
        entity.setPosition(position);
        entity.setStatus(InterviewStatus.PENDING);
        entity.setTranscriptJson(json.toJson(transcript));
        repository.save(entity);
        return entity.getId();
    }

    /**
     * Создаёт интервью из медиа-файла: запускает асинхронную цепочку
     * транскрипция → оценка. Возвращает id интервью.
     */
    @Transactional
    public UUID createFromMedia(String position, byte[] media, String contentType) {
        InterviewEntity entity = new InterviewEntity();
        entity.setId(UUID.randomUUID());
        entity.setPosition(position);
        entity.setStatus(InterviewStatus.TRANSCRIBING);
        // транскрипт ещё не готов — но колонка NOT NULL: пишем пустой массив сегментов
        entity.setTranscriptJson("{\"segments\":[]}");
        repository.saveAndFlush(entity);
        asyncTranscriber.run(entity.getId(), media, contentType);
        return entity.getId();
    }

    @Transactional(readOnly = true)
    public InterviewEntity findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new InterviewNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Transcript getTranscript(UUID id) {
        return json.fromJson(findById(id).getTranscriptJson(), Transcript.class);
    }

    @Transactional(readOnly = true)
    public Optional<EvaluationResult> getResult(UUID id) {
        InterviewEntity entity = findById(id);
        if (entity.getStatus() != InterviewStatus.DONE || entity.getResultJson() == null) {
            return Optional.empty();
        }
        return Optional.of(json.fromJson(entity.getResultJson(), EvaluationResult.class));
    }

    /**
     * Запускает оценку в фоне. Возвращает интервью с обновлённым статусом {@code PENDING},
     * фактическая работа продолжится в {@link AsyncEvaluator#run(UUID)}.
     */
    @Transactional
    public InterviewEntity startEvaluation(UUID id) {
        InterviewEntity entity = findById(id);
        entity.setStatus(InterviewStatus.PENDING);
        entity.setResultJson(null);
        entity.setErrorMessage(null);
        repository.saveAndFlush(entity);
        asyncEvaluator.run(id);
        return entity;
    }
}
