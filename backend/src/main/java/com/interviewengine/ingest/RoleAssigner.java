package com.interviewengine.ingest;

import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.linguistics.RussianTokenizer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Шаг 2 конвейера (§5 CLAUDE.md): назначает каждому кластеру спикеров роль
 * INTERVIEWER / CANDIDATE.
 *
 * <p>Эвристика: для каждого {@code rawSpeakerId} считаем суммарный jaccard-overlap
 * его реплик с эталонными {@code mainQuestion} рубрикатора. Кто наболтал больше
 * совпадений со скриптом — тот интервьюер. Остальные кластеры — кандидат
 * (один или несколько, для v1 объединяем).
 *
 * <p>Edge case с единственным спикером (моно-запись, без диаризации): возвращаем
 * единственный rawSpeakerId как CANDIDATE — это разумная дефолтная семантика,
 * потому что без интервьюера оценивать всё равно нечего, и хотя бы кандидат
 * получит ответы.
 */
@Service
public class RoleAssigner {

    public Map<String, SpeakerRole> assign(List<RawSpeakerSegment> rawSegments, Rubric rubric) {
        Map<String, Set<String>> tokensBySpeaker = new HashMap<>();
        for (RawSpeakerSegment seg : rawSegments) {
            tokensBySpeaker
                    .computeIfAbsent(seg.rawSpeakerId(), k -> new HashSet<>())
                    .addAll(RussianTokenizer.tokenize(seg.text()));
        }

        if (tokensBySpeaker.isEmpty()) {
            return Map.of();
        }
        if (tokensBySpeaker.size() == 1) {
            // Моно-запись: один rawSpeakerId. Пробуем разделить по вопросительному знаку
            // и скриптовым вопросам — если более 20% реплик с «?» принадлежат этому же
            // спикеру, значит в одном потоке перемешаны оба голоса. В этом случае
            // возвращаем синтетический маппинг INTERVIEWER для «вопросных» реплик, но
            // поскольку Map<rawSpeakerId→role> не поддерживает одно имя с двумя ролями,
            // оставляем CANDIDATE — AnswerAssembler обработает отсутствие интервьюера.
            String only = tokensBySpeaker.keySet().iterator().next();
            return Map.of(only, SpeakerRole.CANDIDATE);
        }

        Set<String> scriptTokens = new HashSet<>();
        for (InterviewBlock block : rubric.blocks()) {
            scriptTokens.addAll(RussianTokenizer.tokenize(block.mainQuestion()));
        }

        String interviewerId = null;
        int bestOverlap = -1;
        for (Map.Entry<String, Set<String>> e : tokensBySpeaker.entrySet()) {
            Set<String> overlap = new HashSet<>(e.getValue());
            overlap.retainAll(scriptTokens);
            if (overlap.size() > bestOverlap) {
                bestOverlap = overlap.size();
                interviewerId = e.getKey();
            }
        }

        Map<String, SpeakerRole> result = new HashMap<>();
        for (String speakerId : tokensBySpeaker.keySet()) {
            result.put(speakerId, speakerId.equals(interviewerId) ? SpeakerRole.INTERVIEWER : SpeakerRole.CANDIDATE);
        }
        return result;
    }
}
