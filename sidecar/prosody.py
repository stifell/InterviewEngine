"""
Извлечение просодических характеристик речевого сигнала через Praat (parselmouth).

Просодика — это «как» сказано, в отличие от лексики («что» сказано): темп, паузы,
интонация (F0), громкость. Эти признаки извлекаются прямо из аудио и дополняют
лексический слой при оценке беглости и уверенности речи кандидата.

Главная функция — extract_prosody(): по интервалу [start, end] исходного звука и
списку слов ASR (с таймкодами) возвращает dict, строго совпадающий с Java-record
com.interviewengine.domain.ProsodicFeatures. При любой ошибке возвращает None —
просодика опциональна и не должна валить транскрипцию.
"""

from __future__ import annotations

import logging
import math
from typing import Any

log = logging.getLogger("sidecar.prosody")

# Пауза = разрыв между словами длиннее порога. 250 мс — типичная граница между
# естественной микропаузой артикуляции и заметной паузой/заминкой.
_PAUSE_THRESHOLD_S = 0.25

_warned_unavailable = False


def _round(x: float, n: int = 2) -> float:
    if x is None or (isinstance(x, float) and (math.isnan(x) or math.isinf(x))):
        return 0.0
    return round(float(x), n)


def extract_prosody(sound: Any, start_s: float, end_s: float, words: list) -> dict | None:
    """
    Считает просодику фрагмента звука [start_s, end_s].

    :param sound: parselmouth.Sound всего канала (или None — тогда вернётся None)
    :param words: список объектов слов faster-whisper (атрибуты .start/.end/.word)
    :return: dict с ключами ProsodicFeatures или None при ошибке/отсутствии данных
    """
    global _warned_unavailable
    duration_s = max(0.0, (end_s or 0.0) - (start_s or 0.0))
    if sound is None or duration_s <= 0.05:
        return None

    try:
        import parselmouth  # noqa: F401  (импорт здесь, чтобы sidecar поднимался и без либы)
    except ImportError:
        if not _warned_unavailable:
            log.warning("parselmouth не установлен — просодика недоступна (pip install praat-parselmouth)")
            _warned_unavailable = True
        return None

    try:
        segment = sound.extract_part(from_time=start_s, to_time=end_s, preserve_times=False)

        pitch_mean, pitch_var_st, voiced_ratio = _pitch_features(segment)
        intensity_mean, intensity_var = _intensity_features(segment)
        (speech_rate, articulation_rate, pause_ratio,
         pause_count, mean_pause_ms) = _temporal_features(words, start_s, end_s, duration_s)

        return {
            "speechRateWpm": _round(speech_rate),
            "articulationRateWpm": _round(articulation_rate),
            "pauseRatio": _round(pause_ratio, 3),
            "pauseCount": int(pause_count),
            "meanPauseMs": _round(mean_pause_ms),
            "pitchMeanHz": _round(pitch_mean),
            "pitchVariationSemitones": _round(pitch_var_st),
            "intensityMeanDb": _round(intensity_mean),
            "intensityVariationDb": _round(intensity_var),
            "voicedRatio": _round(voiced_ratio, 3),
            "durationMs": int(duration_s * 1000),
        }
    except Exception as e:  # noqa: BLE001 — просодика не должна ломать транскрипцию
        log.warning(f"Не удалось извлечь просодику [{start_s:.2f}–{end_s:.2f}]: {e}")
        return None


def _pitch_features(segment: Any) -> tuple[float, float, float]:
    """F0: средний тон (Гц), вариативность интонации (σ в полутонах), доля озвучки."""
    import numpy as np

    pitch = segment.to_pitch()
    f0 = pitch.selected_array["frequency"]  # 0.0 там, где нет голоса
    if f0.size == 0:
        return 0.0, 0.0, 0.0

    voiced = f0[f0 > 0]
    voiced_ratio = float(voiced.size) / float(f0.size)
    if voiced.size < 2:
        return (float(voiced.mean()) if voiced.size else 0.0), 0.0, voiced_ratio

    pitch_mean = float(voiced.mean())
    # Вариативность интонации в полутонах относительно медианы — мера выразительности.
    median = float(np.median(voiced))
    semitones = 12.0 * np.log2(voiced / median)
    pitch_var_st = float(np.std(semitones))
    return pitch_mean, pitch_var_st, voiced_ratio


def _intensity_features(segment: Any) -> tuple[float, float]:
    """Интенсивность (громкость): средняя и вариативность, дБ."""
    import numpy as np

    # to_intensity требует, чтобы длительность была > 6.4 / minimum_pitch.
    # При minimum_pitch=100 это ~64 мс; для совсем коротких фрагментов пропускаем.
    if segment.get_total_duration() < 0.07:
        return 0.0, 0.0
    intensity = segment.to_intensity(minimum_pitch=100)
    values = intensity.values[0]
    values = values[np.isfinite(values)]
    if values.size == 0:
        return 0.0, 0.0
    return float(values.mean()), float(values.std())


def _temporal_features(
    words: list, start_s: float, end_s: float, duration_s: float
) -> tuple[float, float, float, int, float]:
    """Темп речи, темп артикуляции, паузы — из таймкодов слов ASR."""
    in_window = [
        w for w in (words or [])
        if getattr(w, "start", None) is not None
        and w.start is not None and w.end is not None
        and w.end > start_s and w.start < end_s
    ]
    word_count = len(in_window)
    if word_count == 0:
        return 0.0, 0.0, 0.0, 0, 0.0

    speech_rate = word_count / duration_s * 60.0

    # Паузы — разрывы между соседними словами длиннее порога.
    in_window.sort(key=lambda w: w.start)
    pause_total_s = 0.0
    pause_count = 0
    for prev, nxt in zip(in_window, in_window[1:]):
        gap = nxt.start - prev.end
        if gap > _PAUSE_THRESHOLD_S:
            pause_total_s += gap
            pause_count += 1

    pause_ratio = min(1.0, pause_total_s / duration_s) if duration_s > 0 else 0.0
    mean_pause_ms = (pause_total_s / pause_count * 1000.0) if pause_count else 0.0

    speaking_s = max(1e-6, duration_s - pause_total_s)
    articulation_rate = word_count / speaking_s * 60.0

    return speech_rate, articulation_rate, pause_ratio, pause_count, mean_pause_ms
