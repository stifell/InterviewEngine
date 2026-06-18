package com.interviewengine.evaluation;

import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.Probe;
import com.interviewengine.domain.ProsodicFeatures;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Шаг 4 конвейера (§5): раскладывает реплики кандидата по основным вопросам блоков
 * и по пробам.
 *
 * <h3>Модель сопоставления</h3>
 * Внутри одного блока интервьюер сначала читает основной вопрос, затем по очереди задаёт
 * запланированные пробы. Тогда соответствие выводится структурно по порядку, без сравнения
 * текстов: первая реплика INTERVIEWER в блоке = основной вопрос, последующие — пробы 1..N
 * в порядке рубрикатора. Реплики CANDIDATE между ходами интервьюера прикрепляются к
 * предыдущему ходу.
 *
 * <p>Если интервьюер задал меньше проб, чем в шаблоне, лишние пробы остаются без ответа —
 * это попадёт в InterviewerEvaluation как пропущенная проба (шаг 8).
 */
@Service
public class AnswerAssembler {

    public AssembledAnswers assemble(Transcript transcript, Rubric rubric) {
        Map<String, StringBuilder> mainByBlock = new LinkedHashMap<>();
        Map<String, StringBuilder> probeByProbeId = new LinkedHashMap<>();

        // Параллельно с текстом копим просодику сегментов кандидата, чтобы потом
        // агрегировать её по каждому ответу (взвешенно по длительности).
        Map<String, List<ProsodicFeatures>> mainProsodyParts = new LinkedHashMap<>();
        Map<String, List<ProsodicFeatures>> probeProsodyParts = new LinkedHashMap<>();

        Map<String, List<Probe>> probesByBlock = new LinkedHashMap<>();
        for (InterviewBlock block : rubric.blocks()) {
            probesByBlock.put(block.id(), block.probes());
        }

        // Состояние обхода по блокам: какая «реплика интервьюера» сейчас активна?
        // -1 = ещё не было ни одной (т.е. ничего записывать)
        //  0 = главный вопрос блока (пишем в mainByBlock)
        //  k = k-я проба блока (1-based) (пишем в probeByProbeId[probes[k-1].id])
        Map<String, Integer> turnIndexByBlock = new LinkedHashMap<>();

        // Отслеживаем, был ли предыдущий сегмент в этом блоке тоже INTERVIEWER.
        // Подряд идущие реплики интервьюера (приветствие + вопрос) считаются одним ходом.
        Map<String, SpeakerRole> lastSpeakerByBlock = new LinkedHashMap<>();

        for (TranscriptSegment seg : transcript.segments()) {
            if (seg.blockId() == null) {
                continue;
            }
            int turn = turnIndexByBlock.getOrDefault(seg.blockId(), -1);
            SpeakerRole lastSpeaker = lastSpeakerByBlock.get(seg.blockId());

            if (seg.speaker() == SpeakerRole.INTERVIEWER) {
                // Увеличиваем счётчик только при смене роли CANDIDATE→INTERVIEWER,
                // а не при каждой реплике интервьюера подряд
                if (lastSpeaker != SpeakerRole.INTERVIEWER) {
                    turn = turn + 1;
                    turnIndexByBlock.put(seg.blockId(), turn);
                }
                lastSpeakerByBlock.put(seg.blockId(), SpeakerRole.INTERVIEWER);
                continue;
            }

            lastSpeakerByBlock.put(seg.blockId(), SpeakerRole.CANDIDATE);

            // SpeakerRole.CANDIDATE
            if (turn < 0) {
                // Кандидат заговорил раньше интервьюера (или интервьюера нет — моно-запись).
                // Добавляем как ответ на главный вопрос: это лучше, чем потерять весь ответ.
                appendTo(mainByBlock, seg.blockId(), seg.text());
                collectProsody(mainProsodyParts, seg.blockId(), seg);
                continue;
            }
            if (turn == 0) {
                appendTo(mainByBlock, seg.blockId(), seg.text());
                collectProsody(mainProsodyParts, seg.blockId(), seg);
            } else {
                List<Probe> probes = probesByBlock.getOrDefault(seg.blockId(), List.of());
                int probeIdx = turn - 1;
                if (probeIdx < probes.size()) {
                    String probeId = probes.get(probeIdx).id();
                    appendTo(probeByProbeId, probeId, seg.text());
                    collectProsody(probeProsodyParts, probeId, seg);
                }
                // если интервьюер задал больше «доп.вопросов», чем запланировано —
                // лишние реплики никуда не относим (для веха 4 этого достаточно)
            }
        }

        return new AssembledAnswers(
                finalize(mainByBlock),
                finalize(probeByProbeId),
                aggregateProsody(mainProsodyParts),
                aggregateProsody(probeProsodyParts)
        );
    }

    /** Копит просодику сегмента кандидата под ключом ответа (если она есть). */
    private static void collectProsody(Map<String, List<ProsodicFeatures>> target, String key, TranscriptSegment seg) {
        if (seg.prosody() == null) {
            return;
        }
        target.computeIfAbsent(key, k -> new ArrayList<>()).add(seg.prosody());
    }

    private static Map<String, ProsodicFeatures> aggregateProsody(Map<String, List<ProsodicFeatures>> parts) {
        Map<String, ProsodicFeatures> out = new LinkedHashMap<>();
        parts.forEach((k, list) -> {
            ProsodicFeatures aggregated = ProsodicFeatures.aggregate(list);
            if (aggregated != null) {
                out.put(k, aggregated);
            }
        });
        return out;
    }

    private static void appendTo(Map<String, StringBuilder> target, String key, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        target.computeIfAbsent(key, k -> new StringBuilder())
                .append(target.get(key).isEmpty() ? "" : " ")
                .append(text.trim());
    }

    private static Map<String, String> finalize(Map<String, StringBuilder> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        raw.forEach((k, v) -> out.put(k, v.toString()));
        return out;
    }
}
