package com.interviewengine.evaluation;

import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.InterviewerEvaluation;
import com.interviewengine.domain.NeutralityFlag;
import com.interviewengine.domain.Probe;
import com.interviewengine.domain.Rubric;
import com.interviewengine.domain.SpeakerRole;
import com.interviewengine.domain.TimingDeviation;
import com.interviewengine.domain.Transcript;
import com.interviewengine.domain.TranscriptSegment;
import com.interviewengine.linguistics.RussianTokenizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Шаг 8 конвейера — полная оценка интервьюера (§5 CLAUDE.md): покрытие проб и
 * основных вопросов, соответствие скрипту, нейтральность, тайминг.
 *
 * <p><b>Соответствие скрипту</b> считается через Jaccard-сходство по токенам между репликами
 * интервьюера и эталонными вопросами рубрикатора по принципу best-match: для каждого
 * скриптового вопроса берётся максимум сходства по всем ходам интервьюера в его блоке.
 * Это намеренно простое лексическое сравнение — без морфологии и эмбеддингов; для v1 его
 * достаточно, чтобы поймать «отклонение от скрипта», когда интервьюер импровизирует, и при
 * этом не зависеть от порядка вопросов и приветствий.
 *
 * <p><b>Нейтральность</b> — словарь маркеров-фраз («не так ли?», «то есть вы имели в виду»,
 * «правильно я понимаю»). Каждое срабатывание — это {@link NeutralityFlag} с цитатой и
 * типом ({@link NeutralityFlag.Kind}).
 *
 * <p><b>Тайминг</b> анализируется только если в сегментах транскрипта есть {@code startMs};
 * иначе список отклонений пуст.
 */
@Service
public class InterviewerEvaluator {

    /** Порог Jaccard для признания реплики соответствующей скрипту. */
    private static final double ADHERENCE_THRESHOLD = 0.5;

    private static final Map<String, NeutralityFlag.Kind> NEUTRALITY_DICT = buildNeutralityDict();

    public InterviewerEvaluation evaluate(Transcript transcript, Rubric rubric, AssembledAnswers assembled) {
        // --- покрытие проб и основных вопросов (как в вехе 4) ---
        List<String> allProbeIds = new ArrayList<>();
        for (InterviewBlock block : rubric.blocks()) {
            for (Probe probe : block.probes()) {
                allProbeIds.add(probe.id());
            }
        }
        List<String> missed = new ArrayList<>();
        for (String probeId : allProbeIds) {
            String ans = assembled.probeAnswer(probeId);
            if (ans == null || ans.isBlank()) {
                missed.add(probeId);
            }
        }
        double probeCoverage = allProbeIds.isEmpty()
                ? 1.0
                : (double) (allProbeIds.size() - missed.size()) / allProbeIds.size();

        int blocksWithMain = 0;
        for (InterviewBlock block : rubric.blocks()) {
            String main = assembled.mainAnswer(block.id());
            if (main != null && !main.isBlank()) {
                blocksWithMain++;
            }
        }
        double mainQuestionCoverage = rubric.blocks().isEmpty()
                ? 1.0
                : (double) blocksWithMain / rubric.blocks().size();

        // --- соответствие скрипту: jaccard над репликами интервьюера ---
        double scriptAdherence = computeScriptAdherence(transcript, rubric);

        // --- нейтральность: словарный поиск маркеров в репликах интервьюера ---
        List<NeutralityFlag> neutralityFlags = detectNeutralityFlags(transcript);

        // --- тайминг: только если есть таймстемпы ---
        List<TimingDeviation> timingDeviations = computeTimingDeviations(transcript, rubric);

        return new InterviewerEvaluation(
                mainQuestionCoverage,
                probeCoverage,
                scriptAdherence,
                missed,
                neutralityFlags,
                timingDeviations,
                List.of()
        );
    }

    // -------- script adherence ----------

    private double computeScriptAdherence(Transcript transcript, Rubric rubric) {
        // Соберём все эталонные тексты по блокам: главный вопрос + пробы.
        Map<String, List<String>> expectedByBlock = new LinkedHashMap<>();
        for (InterviewBlock block : rubric.blocks()) {
            List<String> expected = new ArrayList<>();
            expected.add(block.mainQuestion());
            for (Probe p : block.probes()) {
                expected.add(p.text());
            }
            expectedByBlock.put(block.id(), expected);
        }

        // Склеиваем подряд идущие реплики интервьюера в логические ходы (один вопрос = один ход).
        // pyannote дробит речь на короткие сегменты — без склейки Jaccard никогда не достигает порога.
        Map<String, List<String>> interviewerTurnsByBlock = new LinkedHashMap<>();
        Map<String, SpeakerRole> lastSpeaker = new LinkedHashMap<>();
        for (TranscriptSegment seg : transcript.segments()) {
            if (seg.blockId() == null) continue;
            if (seg.speaker() == SpeakerRole.INTERVIEWER) {
                List<String> turns = interviewerTurnsByBlock.computeIfAbsent(seg.blockId(), k -> new ArrayList<>());
                if (lastSpeaker.get(seg.blockId()) == SpeakerRole.INTERVIEWER && !turns.isEmpty()) {
                    // Продолжение того же хода — склеиваем
                    turns.set(turns.size() - 1, turns.get(turns.size() - 1) + " " + seg.text());
                } else {
                    // Новый ход (после CANDIDATE или первый)
                    turns.add(seg.text());
                }
                lastSpeaker.put(seg.blockId(), SpeakerRole.INTERVIEWER);
            } else {
                lastSpeaker.put(seg.blockId(), SpeakerRole.CANDIDATE);
            }
        }

        // Best-match вместо позиционного сравнения: для каждого эталонного вопроса берём
        // МАКСИМУМ Jaccard по всем ходам интервьюера в блоке. Это отвечает на вопрос «был ли
        // вообще задан этот скриптовый вопрос», не ломаясь от порядка вопросов, приветствий и
        // лишних уточнений (позиционное сравнение их сдвигало и сильно занижало показатель).
        // Знаменатель — эталонные вопросы блоков, где интервьюер реально говорил; блоки без
        // его реплик не штрафуем (их покрытие меряют mainQuestionCoverage/probeCoverage).
        int adherent = 0;
        int total = 0;
        for (InterviewBlock block : rubric.blocks()) {
            List<String> turns = interviewerTurnsByBlock.get(block.id());
            if (turns == null || turns.isEmpty()) {
                continue;
            }
            for (String expected : expectedByBlock.getOrDefault(block.id(), List.of())) {
                total++;
                double best = 0.0;
                for (String turn : turns) {
                    best = Math.max(best, jaccard(turn, expected));
                }
                if (best >= ADHERENCE_THRESHOLD) {
                    adherent++;
                }
            }
        }
        return total == 0 ? 1.0 : (double) adherent / total;
    }

    private static double jaccard(String a, String b) {
        Set<String> ta = new HashSet<>(RussianTokenizer.tokenize(a));
        Set<String> tb = new HashSet<>(RussianTokenizer.tokenize(b));
        if (ta.isEmpty() && tb.isEmpty()) return 1.0;
        Set<String> intersection = new HashSet<>(ta);
        intersection.retainAll(tb);
        Set<String> union = new HashSet<>(ta);
        union.addAll(tb);
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    // -------- нейтральность ----------

    private List<NeutralityFlag> detectNeutralityFlags(Transcript transcript) {
        List<NeutralityFlag> flags = new ArrayList<>();
        for (TranscriptSegment seg : transcript.segments()) {
            if (seg.speaker() != SpeakerRole.INTERVIEWER) continue;
            String normalized = " " + seg.text().toLowerCase(Locale.ROOT) + " ";
            for (Map.Entry<String, NeutralityFlag.Kind> e : NEUTRALITY_DICT.entrySet()) {
                if (normalized.contains(" " + e.getKey() + " ")
                        || normalized.contains(" " + e.getKey() + ",")
                        || normalized.contains(" " + e.getKey() + "?")
                        || normalized.contains(" " + e.getKey() + ".")) {
                    flags.add(new NeutralityFlag(e.getValue(), seg.blockId(), e.getKey(), seg.text().trim()));
                    break; // один флаг на реплику достаточно
                }
            }
        }
        return flags;
    }

    private static Map<String, NeutralityFlag.Kind> buildNeutralityDict() {
        Map<String, NeutralityFlag.Kind> dict = new HashMap<>();

        // Подтверждающие/поддакивающие фразы (давление на согласие)
        for (String s : List.of(
                "не так ли", "верно", "правильно", "согласны", "согласен",
                "правда", "понятно", "ясно", "договорились"
        )) {
            dict.put(s, NeutralityFlag.Kind.SUGGESTIVE_AGREEMENT);
        }

        // Переформулирование ответа в нужную сторону
        for (String s : List.of(
                "то есть вы имели в виду", "правильно я понимаю", "иными словами",
                "если я правильно понял", "получается"
        )) {
            dict.put(s, NeutralityFlag.Kind.ANSWER_REFRAMING);
        }

        // Прямые подсказки ответа
        for (String s : List.of(
                "вы наверное", "вы, наверное", "обычно делают через", "обычно используют",
                "может быть это", "скорее всего вы", "наверное вы использовали"
        )) {
            dict.put(s, NeutralityFlag.Kind.ANSWER_HINT);
        }

        return dict;
    }

    // -------- тайминг ----------

    private List<TimingDeviation> computeTimingDeviations(Transcript transcript, Rubric rubric) {
        // first start / last end per block
        Map<String, long[]> spanByBlock = new LinkedHashMap<>();
        for (TranscriptSegment seg : transcript.segments()) {
            if (seg.startMs() == null || seg.endMs() == null || seg.blockId() == null) continue;
            long[] span = spanByBlock.get(seg.blockId());
            if (span == null) {
                spanByBlock.put(seg.blockId(), new long[]{seg.startMs(), seg.endMs()});
            } else {
                span[0] = Math.min(span[0], seg.startMs());
                span[1] = Math.max(span[1], seg.endMs());
            }
        }
        if (spanByBlock.isEmpty()) {
            return List.of();
        }

        List<TimingDeviation> result = new ArrayList<>();
        for (InterviewBlock block : rubric.blocks()) {
            long[] span = spanByBlock.get(block.id());
            if (span == null) continue;
            double actualMinutes = (span[1] - span[0]) / 60_000.0;
            double deviation = actualMinutes - block.timingMinutes();
            result.add(new TimingDeviation(block.id(), block.timingMinutes(), actualMinutes, deviation));
        }
        return result;
    }
}
