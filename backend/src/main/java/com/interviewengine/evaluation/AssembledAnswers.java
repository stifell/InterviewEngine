package com.interviewengine.evaluation;

import com.interviewengine.domain.ProsodicFeatures;

import java.util.Map;

/**
 * Промежуточный результат шага 4 конвейера: транскрипт, разложенный по основным
 * вопросам блоков и по пробам — текст и (если был аудиовход) агрегированная просодика.
 *
 * @param mainAnswerByBlock      blockId → склеенный ответ кандидата на основной вопрос блока
 * @param probeAnswerByProbeId   probeId → склеенный ответ кандидата на эту пробу
 *                               (отсутствие ключа = проба не задавалась)
 * @param mainProsodyByBlock     blockId → агрегированная просодика ответа (nullable-значения)
 * @param probeProsodyByProbeId  probeId → агрегированная просодика ответа (nullable-значения)
 */
public record AssembledAnswers(
        Map<String, String> mainAnswerByBlock,
        Map<String, String> probeAnswerByProbeId,
        Map<String, ProsodicFeatures> mainProsodyByBlock,
        Map<String, ProsodicFeatures> probeProsodyByProbeId
) {
    public AssembledAnswers {
        mainAnswerByBlock = mainAnswerByBlock == null ? Map.of() : Map.copyOf(mainAnswerByBlock);
        probeAnswerByProbeId = probeAnswerByProbeId == null ? Map.of() : Map.copyOf(probeAnswerByProbeId);
        // Просодика может содержать null-значения → обычный HashMap, не Map.copyOf (тот NPE на null).
        mainProsodyByBlock = mainProsodyByBlock == null
                ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.HashMap<>(mainProsodyByBlock));
        probeProsodyByProbeId = probeProsodyByProbeId == null
                ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.HashMap<>(probeProsodyByProbeId));
    }

    /** Совместимый конструктор без просодики (текстовые транскрипты, тесты). */
    public AssembledAnswers(
            Map<String, String> mainAnswerByBlock,
            Map<String, String> probeAnswerByProbeId
    ) {
        this(mainAnswerByBlock, probeAnswerByProbeId, Map.of(), Map.of());
    }

    public String mainAnswer(String blockId) {
        return mainAnswerByBlock.getOrDefault(blockId, "");
    }

    public String probeAnswer(String probeId) {
        return probeAnswerByProbeId.getOrDefault(probeId, "");
    }

    /** Агрегированная просодика основного ответа блока или {@code null}, если аудио не было. */
    public ProsodicFeatures mainProsody(String blockId) {
        return mainProsodyByBlock.get(blockId);
    }

    /** Агрегированная просодика ответа на пробу или {@code null}. */
    public ProsodicFeatures probeProsody(String probeId) {
        return probeProsodyByProbeId.get(probeId);
    }
}
