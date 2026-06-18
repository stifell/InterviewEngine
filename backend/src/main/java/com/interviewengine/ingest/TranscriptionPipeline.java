package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.rubric.RubricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Оркестратор шагов 1–3 конвейера (§5 CLAUDE.md): bytes → транскрипт + назначенные
 * роли + привязка к блокам.
 *
 * <p>Разделение ролей возможно только при стерео-записи (два канала) или при
 * диаризации через pyannote. Для моно-аудио (один канал / все сегменты = spk0)
 * все реплики помечаются CANDIDATE — AnswerAssembler обрабатывает этот случай
 * через проверку turn < 0 и берёт весь блок как ответ.
 */
@Service
public class TranscriptionPipeline {

    private static final Logger log = LoggerFactory.getLogger(TranscriptionPipeline.class);

    private final Transcriber transcriber;
    private final RoleAssigner roleAssigner;
    private final BlockClassifier blockClassifier;
    private final RubricLoader rubricLoader;

    public TranscriptionPipeline(
            Transcriber transcriber,
            RoleAssigner roleAssigner,
            BlockClassifier blockClassifier,
            RubricLoader rubricLoader
    ) {
        this.transcriber = transcriber;
        this.roleAssigner = roleAssigner;
        this.blockClassifier = blockClassifier;
        this.rubricLoader = rubricLoader;
    }

    public Transcript transcribe(byte[] media, String contentType, String position) {
        Rubric rubric = rubricLoader.loadByPosition(position);
        List<RawSpeakerSegment> rawSegments = transcriber.transcribe(media, contentType);
        if (rawSegments.isEmpty()) {
            return new Transcript(List.of());
        }

        Map<String, SpeakerRole> rolesBySpeaker = roleAssigner.assign(rawSegments, rubric);
        List<String> blockIds = blockClassifier.classify(rawSegments, rubric);

        boolean isMono = rolesBySpeaker.size() == 1
                && rolesBySpeaker.containsValue(SpeakerRole.CANDIDATE);
        if (isMono) {
            log.info("TranscriptionPipeline: моно-аудио — все сегменты CANDIDATE, роли не разделены");
        }

        List<TranscriptSegment> segments = new ArrayList<>(rawSegments.size());
        for (int i = 0; i < rawSegments.size(); i++) {
            RawSpeakerSegment raw = rawSegments.get(i);
            String blockId = blockIds.get(i);
            if (blockId == null) continue;
            SpeakerRole role = rolesBySpeaker.getOrDefault(raw.rawSpeakerId(), SpeakerRole.CANDIDATE);
            segments.add(new TranscriptSegment(role, blockId, raw.text(), raw.startMs(), raw.endMs(), raw.prosody()));
        }

        long iCount = segments.stream().filter(s -> s.speaker() == SpeakerRole.INTERVIEWER).count();
        long cCount = segments.stream().filter(s -> s.speaker() == SpeakerRole.CANDIDATE).count();
        log.info("TranscriptionPipeline: {} сегментов, И={} К={}", segments.size(), iCount, cCount);
        return new Transcript(segments);
    }
}
