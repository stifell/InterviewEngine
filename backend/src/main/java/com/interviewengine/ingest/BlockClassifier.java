package com.interviewengine.ingest;

import com.interviewengine.domain.RawSpeakerSegment;
import com.interviewengine.domain.Rubric;

import java.util.List;

/**
 * Шаг 3 конвейера (§5 CLAUDE.md): для каждого сегмента возвращает id блока
 * рубрикатора, к которому он относится. Если уверенно сопоставить нельзя —
 * возвращается {@code null} в соответствующей позиции списка.
 *
 * <p>v1: эвристика по «ближайшему предшествующему mainQuestion» — сегмент попадает
 * в блок последнего основного вопроса, который интервьюер задал до этого момента.
 * Для v2 (когда увидим реальные расшифровки) можно добавить LLM-классификатор.
 */
public interface BlockClassifier {

    /**
     * @return список той же длины и порядка, что {@code rawSegments}; элементы — blockId или null.
     */
    List<String> classify(List<RawSpeakerSegment> rawSegments, Rubric rubric);
}
