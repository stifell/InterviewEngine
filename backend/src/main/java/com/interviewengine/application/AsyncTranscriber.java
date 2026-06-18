package com.interviewengine.application;

import com.interviewengine.domain.InterviewStatus;
import com.interviewengine.domain.Transcript;
import com.interviewengine.ingest.TranscriptionPipeline;
import com.interviewengine.persistence.InterviewEntity;
import com.interviewengine.persistence.InterviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Асинхронный исполнитель шагов 1–3 конвейера: media → Transcript.
 * После успешной транскрипции автоматически передаёт эстафету {@link AsyncEvaluator}.
 *
 * <p>Media-байты передаются параметрами метода и не сохраняются в БД — для v1 это
 * приемлемо: если процесс перезапустится во время транскрипции, нужно будет
 * заново загрузить файл. Когда добавим storage (S3 / FS), можно будет упорно
 * сохранять и возобновлять.
 */
@Component
public class AsyncTranscriber {

    private static final Logger log = LoggerFactory.getLogger(AsyncTranscriber.class);

    private final InterviewRepository repository;
    private final TranscriptionPipeline transcriptionPipeline;
    private final AsyncEvaluator asyncEvaluator;
    private final JsonCodec json;

    public AsyncTranscriber(
            InterviewRepository repository,
            TranscriptionPipeline transcriptionPipeline,
            AsyncEvaluator asyncEvaluator,
            JsonCodec json
    ) {
        this.repository = repository;
        this.transcriptionPipeline = transcriptionPipeline;
        this.asyncEvaluator = asyncEvaluator;
        this.json = json;
    }

    @Async
    @Transactional
    public void run(UUID interviewId, byte[] media, String contentType) {
        InterviewEntity entity = repository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException(interviewId));

        entity.setStatus(InterviewStatus.TRANSCRIBING);
        entity.setErrorMessage(null);
        repository.saveAndFlush(entity);

        try {
            Transcript transcript = transcriptionPipeline.transcribe(media, contentType, entity.getPosition());
            entity.setTranscriptJson(json.toJson(transcript));
            entity.setStatus(InterviewStatus.PENDING);
            repository.saveAndFlush(entity);   // flush ДО запуска asyncEvaluator — иначе новый поток читает старый пустой транскрипт
            log.info("Транскрипция интервью {} готова, {} сегментов; запускаю оценку",
                    interviewId, transcript.segments().size());
        } catch (RuntimeException e) {
            log.error("Транскрипция интервью {} упала", interviewId, e);
            entity.setStatus(InterviewStatus.FAILED);
            entity.setErrorMessage(e.getMessage());
            repository.save(entity);
            return;
        }

        // Цепочка: после транскрипции сразу запускаем оценку
        asyncEvaluator.run(interviewId);
    }
}
