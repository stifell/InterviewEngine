package com.interviewengine.domain;

/**
 * Отклонение фактического тайминга блока от запланированного. Считается, только
 * если в транскрипте есть таймстемпы (см. {@link TranscriptSegment#startMs()}).
 *
 * @param blockId             id блока
 * @param plannedMinutes      план из рубрикатора
 * @param actualMinutes       фактическая длительность (по first start → last end в блоке)
 * @param deviationMinutes    actual - planned; положительное = блок затянут, отрицательное = слишком быстро
 */
public record TimingDeviation(
        String blockId,
        int plannedMinutes,
        double actualMinutes,
        double deviationMinutes
) {
}
