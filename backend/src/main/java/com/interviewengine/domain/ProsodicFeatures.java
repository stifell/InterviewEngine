package com.interviewengine.domain;

import java.util.List;

/**
 * Просодические характеристики речевого сигнала одного фрагмента речи кандидата.
 * <p>В отличие от {@link com.interviewengine.linguistics.LinguisticFeatures} (текст),
 * эти признаки извлекаются из самого аудио (F0, интенсивность, темп, паузы) в
 * Python-сайдкаре через Praat/parselmouth и приходят уже посчитанными.
 *
 * <p>Доступны только когда вход — аудио/видео и работает {@code SidecarTranscriber}.
 * Для текстовых интервью и {@code GeminiTranscriber} значение — {@code null} на всём
 * пути конвейера (см. §5.1 CLAUDE.md).
 *
 * @param speechRateWpm           темп речи: слов в минуту по всей длительности фрагмента
 * @param articulationRateWpm     темп артикуляции: слов в минуту без учёта пауз (чистая речь)
 * @param pauseRatio              доля пауз в длительности фрагмента [0..1]
 * @param pauseCount              число заметных пауз (gap между словами > порога)
 * @param meanPauseMs             средняя длительность паузы, мс
 * @param pitchMeanHz             средняя частота основного тона F0, Гц
 * @param pitchVariationSemitones вариативность интонации: σ(F0) в полутонах (выразительность)
 * @param intensityMeanDb         средняя интенсивность (громкость), дБ
 * @param intensityVariationDb    вариативность интенсивности, дБ
 * @param voicedRatio             доля озвученных (вокализованных) фреймов [0..1]
 * @param durationMs              суммарная длительность фрагмента речи, мс (вес при агрегации)
 */
public record ProsodicFeatures(
        double speechRateWpm,
        double articulationRateWpm,
        double pauseRatio,
        int pauseCount,
        double meanPauseMs,
        double pitchMeanHz,
        double pitchVariationSemitones,
        double intensityMeanDb,
        double intensityVariationDb,
        double voicedRatio,
        long durationMs
) {

    /**
     * Агрегирует просодику нескольких сегментов одного ответа в один набор признаков.
     * Непрерывные метрики усредняются взвешенно по длительности сегмента
     * ({@link #durationMs}); {@link #meanPauseMs} — взвешенно по числу пауз;
     * {@link #pauseCount} и {@link #durationMs} суммируются.
     *
     * @param parts просодика сегментов; {@code null}-элементы и нулевые длительности игнорируются
     * @return агрегированная просодика или {@code null}, если валидных данных нет
     */
    public static ProsodicFeatures aggregate(List<ProsodicFeatures> parts) {
        if (parts == null || parts.isEmpty()) {
            return null;
        }

        double totalDuration = 0.0;
        long totalDurationMs = 0L;
        int totalPauses = 0;
        double wSpeechRate = 0, wArticulation = 0, wPauseRatio = 0;
        double wPitchMean = 0, wPitchVar = 0, wIntensityMean = 0, wIntensityVar = 0, wVoiced = 0;
        double pauseWeightedMeanPause = 0;

        for (ProsodicFeatures p : parts) {
            if (p == null || p.durationMs <= 0) {
                continue;
            }
            double w = p.durationMs;
            totalDuration += w;
            totalDurationMs += p.durationMs;
            totalPauses += p.pauseCount;
            wSpeechRate += p.speechRateWpm * w;
            wArticulation += p.articulationRateWpm * w;
            wPauseRatio += p.pauseRatio * w;
            wPitchMean += p.pitchMeanHz * w;
            wPitchVar += p.pitchVariationSemitones * w;
            wIntensityMean += p.intensityMeanDb * w;
            wIntensityVar += p.intensityVariationDb * w;
            wVoiced += p.voicedRatio * w;
            pauseWeightedMeanPause += p.meanPauseMs * p.pauseCount;
        }

        if (totalDuration == 0.0) {
            return null;
        }

        double meanPause = totalPauses > 0 ? pauseWeightedMeanPause / totalPauses : 0.0;
        return new ProsodicFeatures(
                wSpeechRate / totalDuration,
                wArticulation / totalDuration,
                wPauseRatio / totalDuration,
                totalPauses,
                meanPause,
                wPitchMean / totalDuration,
                wPitchVar / totalDuration,
                wIntensityMean / totalDuration,
                wIntensityVar / totalDuration,
                wVoiced / totalDuration,
                totalDurationMs
        );
    }
}
