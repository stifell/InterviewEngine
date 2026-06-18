package com.interviewengine.ingest;

import com.interviewengine.domain.InterviewBlock;
import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;
import com.interviewengine.linguistics.RussianTokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Эвристическая реализация {@link BlockClassifier}: ищет «открывающую» реплику
 * каждого блока (mainQuestion) в потоке сегментов по jaccard-сходству и проводит
 * границу — все последующие сегменты принадлежат этому блоку, пока не найдётся
 * следующий mainQuestion.
 *
 * <p>Реплики до первого распознанного mainQuestion назначаются в первый блок
 * (icebreaker/приветствие). Если Jaccard-матчинг вообще не срабатывает (интервью
 * ведётся своими словами, а не дословно по рубрикатору), применяется time-based
 * fallback: сегменты распределяются пропорционально timingMinutes каждого блока.
 */
@Service
public class HeuristicBlockClassifier implements BlockClassifier {

    private static final Logger log = LoggerFactory.getLogger(HeuristicBlockClassifier.class);

    /** Снижен с 0.35 — реальные интервью не цитируют mainQuestion дословно. */
    private static final double MAIN_QUESTION_MATCH_THRESHOLD = 0.15;

    @Override
    public List<String> classify(List<RawSpeakerSegment> rawSegments, Rubric rubric) {
        List<InterviewBlock> blocks = rubric.blocks();
        if (blocks.isEmpty() || rawSegments.isEmpty()) {
            return new ArrayList<>(rawSegments.stream().map(s -> (String) null).toList());
        }

        List<BlockTokens> blockTokens = new ArrayList<>();
        for (InterviewBlock block : blocks) {
            blockTokens.add(new BlockTokens(block.id(), new HashSet<>(RussianTokenizer.tokenize(block.mainQuestion()))));
        }

        // Сначала вычисляем time-based распределение как надёжный baseline.
        // Затем поверх него применяем Jaccard-поправки: если сегмент явно совпадает
        // с mainQuestion другого блока, переключаем с этого места.
        // Это надёжнее, чем «Jaccard или time-based» — реальные интервью часто ведутся
        // своими словами, порождая ложные Jaccard-срабатывания на пороге 0.15.
        List<String> result = timeBasedClassify(rawSegments, blocks);

        String currentBlock = blocks.get(0).id();
        Set<String> distinctBlocksFound = new HashSet<>();
        distinctBlocksFound.add(currentBlock);

        for (int i = 0; i < rawSegments.size(); i++) {
            Set<String> tokens = new HashSet<>(RussianTokenizer.tokenize(rawSegments.get(i).text()));
            String matchedBlock = bestMatchingBlock(tokens, blockTokens);
            if (matchedBlock != null) {
                // Засчитываем смену блока только если это реальный переход вперёд
                // по порядку, а не случайный возврат к предыдущему блоку
                int matchedOrder = blockOrder(matchedBlock, blocks);
                int currentOrder = blockOrder(currentBlock, blocks);
                if (matchedOrder >= currentOrder) {
                    currentBlock = matchedBlock;
                    distinctBlocksFound.add(currentBlock);
                }
            }
            result.set(i, currentBlock);
        }

        log.info("BlockClassifier: Jaccard нашёл {} из {} блоков в {} сегментах",
                distinctBlocksFound.size(), blocks.size(), rawSegments.size());
        return result;
    }

    /**
     * Распределяет сегменты по блокам пропорционально их timingMinutes.
     * Опирается на startMs последнего сегмента как на общую длительность аудио.
     */
    private static List<String> timeBasedClassify(List<RawSpeakerSegment> rawSegments, List<InterviewBlock> blocks) {
        long audioEndMs = rawSegments.get(rawSegments.size() - 1).endMs();
        int totalMinutes = blocks.stream().mapToInt(InterviewBlock::timingMinutes).sum();

        // Границы блоков в ms (пропорционально)
        List<Long> blockEndMs = new ArrayList<>();
        long elapsed = 0;
        for (int i = 0; i < blocks.size() - 1; i++) {
            elapsed += (long) audioEndMs * blocks.get(i).timingMinutes() / totalMinutes;
            blockEndMs.add(elapsed);
        }
        blockEndMs.add(Long.MAX_VALUE); // последний блок захватывает всё остальное

        List<String> result = new ArrayList<>(rawSegments.size());
        for (RawSpeakerSegment seg : rawSegments) {
            long mid = (seg.startMs() + seg.endMs()) / 2;
            String blockId = blocks.get(0).id();
            for (int j = 0; j < blockEndMs.size(); j++) {
                if (mid <= blockEndMs.get(j)) {
                    blockId = blocks.get(j).id();
                    break;
                }
            }
            result.add(blockId);
        }
        return result;
    }

    private static String bestMatchingBlock(Set<String> tokens, List<BlockTokens> blocks) {
        String best = null;
        double bestScore = MAIN_QUESTION_MATCH_THRESHOLD;
        for (BlockTokens bt : blocks) {
            double score = jaccard(tokens, bt.tokens);
            if (score >= bestScore) {
                bestScore = score;
                best = bt.blockId;
            }
        }
        return best;
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return union.isEmpty() ? 0.0 : (double) inter.size() / union.size();
    }

    private static int blockOrder(String blockId, List<InterviewBlock> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).id().equals(blockId)) return i;
        }
        return 0;
    }

    private record BlockTokens(String blockId, Set<String> tokens) {
    }
}
