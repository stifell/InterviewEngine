"""
Copyright (c) 2026 Denis Matyushkin (https://github.com/stifell). All Rights Reserved.
Проприетарное ПО. Использование/копирование/распространение без письменного
разрешения автора запрещены. См. файлы LICENSE и NOTICE.

Sidecar для русскоязычной ASR + диаризации.

Архитектура v2:
- ASR: faster-whisper с моделью large-v3-turbo (хороший русский, ~800MB)
- Диаризация:
    1. Стерео с разными каналами → channel-split (interviewer L / candidate R)
    2. Моно → pyannote/speaker-diarization-3.1 (если задан HUGGINGFACE_TOKEN)
    3. Моно без токена → все сегменты spk0 (деградированный режим)

На вход: multipart-файл аудио или видео. Выход: JSON-сегменты с
таймкодами и rawSpeakerId, формат строго совпадает с Java-record
com.interviewengine.domain.RawSpeakerSegment.
"""

from __future__ import annotations

import logging
import os
import subprocess
import tempfile
import wave
from contextlib import asynccontextmanager
from pathlib import Path
from typing import Iterable

from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel

log = logging.getLogger("sidecar")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

# Модели загружаются лениво — первый запрос будет медленнее.
_whisper_model = None
_diarization_pipeline = None


def _register_cuda_dlls() -> None:
    """
    На Windows добавляет в путь поиска DLL каталоги pip-пакетов NVIDIA
    (nvidia-cublas-cu12 / nvidia-cudnn-cu12). Без этого CTranslate2 не находит
    cublas64_12.dll / cudnn_*64_9.dll, даже если пакеты установлены в venv.
    Установка: pip install nvidia-cublas-cu12 nvidia-cudnn-cu12
    """
    if os.name != "nt":
        return
    import importlib.util
    found_any = False
    for pkg in ("nvidia.cublas", "nvidia.cudnn"):
        try:
            spec = importlib.util.find_spec(pkg)
        except ModuleNotFoundError:
            spec = None
        if spec and spec.submodule_search_locations:
            bin_dir = Path(list(spec.submodule_search_locations)[0]) / "bin"
            if bin_dir.is_dir():
                os.add_dll_directory(str(bin_dir))
                log.info(f"CUDA DLL каталог добавлен в путь: {bin_dir}")
                found_any = True
    if not found_any:
        log.warning(
            "CUDA-библиотеки (cuBLAS/cuDNN) не найдены среди pip-пакетов. "
            "Если используете GPU — выполните: "
            "pip install nvidia-cublas-cu12 nvidia-cudnn-cu12. "
            "Либо переключитесь на CPU: WHISPER_DEVICE=cpu WHISPER_COMPUTE_TYPE=int8"
        )


def _get_whisper_model():
    global _whisper_model
    if _whisper_model is None:
        model_size = os.environ.get("WHISPER_MODEL", "large-v3-turbo")
        device = os.environ.get("WHISPER_DEVICE", "cpu")
        compute_type = os.environ.get("WHISPER_COMPUTE_TYPE", "int8" if device == "cpu" else "float16")
        if device == "cuda":
            _register_cuda_dlls()
        from faster_whisper import WhisperModel
        log.info(f"Загружаю whisper-модель: size={model_size} device={device} compute_type={compute_type}")
        _whisper_model = WhisperModel(model_size, device=device, compute_type=compute_type)
    return _whisper_model


def _get_diarization_pipeline():
    """
    Lazy-инициализация pyannote speaker-diarization-3.1.
    Требует HUGGINGFACE_TOKEN в окружении.
    Возвращает None если токен не задан (деградированный режим).
    """
    global _diarization_pipeline
    if _diarization_pipeline is not None:
        return _diarization_pipeline

    hf_token = os.environ.get("HUGGINGFACE_TOKEN")
    if not hf_token:
        log.warning("HUGGINGFACE_TOKEN не задан — диаризация недоступна, используем моно-режим (spk0)")
        return None

    try:
        from pyannote.audio import Pipeline
        import torch
        log.info("Загружаю pyannote/speaker-diarization-3.1 (первый раз — может занять минуту)...")
        _diarization_pipeline = Pipeline.from_pretrained(
            "pyannote/speaker-diarization-3.1",
            token=hf_token,
        )
        # pyannote работает через torch (в отличие от ASR на CTranslate2). На GPU идём
        # только если torch собран с CUDA; иначе — на CPU (медленнее, но диаризация
        # не отваливается). Так WHISPER_DEVICE=cuda не ломает диаризацию при CPU-сборке torch.
        want_device = os.environ.get("WHISPER_DEVICE", "cpu")
        if want_device == "cuda" and not torch.cuda.is_available():
            log.warning("torch собран без CUDA — диаризация пойдёт на CPU (медленнее). "
                        "Для GPU поставьте torch с CUDA: "
                        "pip install torch --index-url https://download.pytorch.org/whl/cu124")
            device = "cpu"
        else:
            device = want_device
        _diarization_pipeline.to(torch.device(device))
        # Проверяем устройство через внутреннюю модель сегментации
        try:
            seg_model = _diarization_pipeline._segmentation.model
            actual_device = next(seg_model.parameters()).device
        except Exception:
            actual_device = torch.device(device)
        log.info(f"pyannote pipeline загружен успешно, устройство: {actual_device}")
    except Exception as e:
        log.error(f"Не удалось загрузить pyannote pipeline: {e}")
        log.warning("Продолжаем без диаризации (все сегменты = spk0)")
        _diarization_pipeline = None  # останется None, не будем пытаться снова

    return _diarization_pipeline


@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("Sidecar стартует. Модели загрузятся при первом /transcribe.")
    yield
    log.info("Sidecar останавливается.")


app = FastAPI(title="interviewengine-sidecar", version="0.3.0", lifespan=lifespan)


class Prosody(BaseModel):
    """Совпадает с com.interviewengine.domain.ProsodicFeatures."""
    speechRateWpm: float
    articulationRateWpm: float
    pauseRatio: float
    pauseCount: int
    meanPauseMs: float
    pitchMeanHz: float
    pitchVariationSemitones: float
    intensityMeanDb: float
    intensityVariationDb: float
    voicedRatio: float
    durationMs: int


class RawSpeakerSegment(BaseModel):
    """Совпадает с com.interviewengine.domain.RawSpeakerSegment."""
    rawSpeakerId: str
    startMs: int
    endMs: int
    text: str
    prosody: Prosody | None = None


class TranscriptionResponse(BaseModel):
    segments: list[RawSpeakerSegment]


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/transcribe", response_model=TranscriptionResponse)
async def transcribe(file: UploadFile = File(...)) -> TranscriptionResponse:
    if not file.filename:
        raise HTTPException(status_code=400, detail="file is required")

    audio_bytes = await file.read()
    log.info(f"Получен файл {file.filename}, {len(audio_bytes)} bytes, content_type={file.content_type}")

    with tempfile.TemporaryDirectory() as tmpdir:
        tmp_path = Path(tmpdir)
        in_path = tmp_path / ("input" + (Path(file.filename).suffix or ".bin"))
        in_path.write_bytes(audio_bytes)

        # 1) Нормализуем в WAV 16кГц через ffmpeg. Сохраняем оба канала.
        wav_path = tmp_path / "audio.wav"
        _ffmpeg_to_wav(in_path, wav_path)

        channels = _wav_channels(wav_path)
        log.info(f"WAV готов: {wav_path.stat().st_size} bytes, channels={channels}")

        if channels == 2 and not _channels_are_identical(wav_path):
            # Настоящее стерео: два разных канала → каждый свой спикер
            log.info("Режим: стерео (channel-split)")
            segments = _transcribe_stereo(wav_path, tmp_path)
        else:
            # Моно или «фейковое стерео»
            if channels == 2:
                log.info("Стерео обнаружено, но каналы идентичны — обрабатываем как моно")
                wav_path = _stereo_to_mono(wav_path, tmp_path)

            log.info("Режим: моно — запускаю ASR + диаризацию")
            segments = _transcribe_mono(wav_path, "spk0")

            # Пробуем pyannote диаризацию поверх ASR
            segments = _apply_diarization(wav_path, segments)

    speakers = set(s.rawSpeakerId for s in segments)
    log.info(f"Готово: {len(segments)} сегментов, спикеры: {speakers}")
    return TranscriptionResponse(segments=segments)


# ──────────────────────────────────────────────────────────────────────────────
# ASR (faster-whisper)
# ──────────────────────────────────────────────────────────────────────────────

def _transcribe_mono(wav_path: Path, speaker_id: str) -> list[RawSpeakerSegment]:
    """Прогон моно-файла через whisper. Все сегменты помечаются одним speaker_id."""
    model = _get_whisper_model()
    segments_iter, info = model.transcribe(
        str(wav_path),
        language="ru",
        vad_filter=True,
        vad_parameters={"min_silence_duration_ms": 500},
        word_timestamps=True,  # нужны для просодики (темп, паузы)
    )
    log.info(f"whisper: language={info.language} prob={info.language_probability:.2f} duration={info.duration:.1f}s")
    sound = _load_sound(wav_path)
    return list(_to_raw_segments(segments_iter, speaker_id, sound))


def _load_sound(wav_path: Path):
    """Загружает parselmouth.Sound для просодики. None, если либа недоступна."""
    try:
        import parselmouth
        return parselmouth.Sound(str(wav_path))
    except ImportError:
        return None
    except Exception as e:  # noqa: BLE001
        log.warning(f"Не удалось загрузить Sound из {wav_path}: {e}")
        return None


def _transcribe_stereo(wav_path: Path, tmpdir: Path) -> list[RawSpeakerSegment]:
    """
    Двухканальный режим: channel 0 = интервьюер (spk0), channel 1 = кандидат (spk1).
    Транскрибируем каналы по отдельности и сливаем по таймкодам.
    """
    left = tmpdir / "left.wav"
    right = tmpdir / "right.wav"

    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-i", str(wav_path),
        "-filter_complex", "[0:a]channelsplit=channel_layout=stereo[L][R]",
        "-map", "[L]", str(left),
        "-map", "[R]", str(right),
        "-y",
    ], check=True, capture_output=True)

    left_segs = _transcribe_mono(left, "spk0")
    right_segs = _transcribe_mono(right, "spk1")

    merged = left_segs + right_segs
    merged.sort(key=lambda s: s.startMs)
    return merged


def _to_raw_segments(segments_iter: Iterable, speaker_id: str, sound=None) -> Iterable[RawSpeakerSegment]:
    from prosody import extract_prosody
    for seg in segments_iter:
        text = (seg.text or "").strip()
        if not text:
            continue
        prosody_dict = extract_prosody(sound, seg.start, seg.end, getattr(seg, "words", None) or [])
        yield RawSpeakerSegment(
            rawSpeakerId=speaker_id,
            startMs=int(seg.start * 1000),
            endMs=int(seg.end * 1000),
            text=text,
            prosody=Prosody(**prosody_dict) if prosody_dict else None,
        )


# ──────────────────────────────────────────────────────────────────────────────
# Диаризация (pyannote)
# ──────────────────────────────────────────────────────────────────────────────

def _apply_diarization(
    wav_path: Path,
    whisper_segs: list[RawSpeakerSegment],
) -> list[RawSpeakerSegment]:
    """
    Запускает pyannote диаризацию и присваивает каждому whisper-сегменту
    speaker id по максимальному перекрытию с диаризационным отрезком.

    Если pyannote недоступен (нет токена / ошибка загрузки) — возвращает
    исходные сегменты без изменений (все spk0, деградированный режим).
    """
    if not whisper_segs:
        return whisper_segs

    pipeline = _get_diarization_pipeline()
    if pipeline is None:
        log.info("Диаризация пропущена — все сегменты остаются spk0")
        return whisper_segs

    try:
        log.info("Запускаю pyannote диаризацию...")
        # torchcodec на Windows не работает — загружаем WAV вручную и передаём тензором
        import torch
        import numpy as np
        with wave.open(str(wav_path), "rb") as w:
            n_frames   = w.getnframes()
            n_channels = w.getnchannels()
            sample_rate = w.getframerate()
            raw = w.readframes(n_frames)
        samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
        if n_channels == 1:
            waveform = torch.from_numpy(samples).unsqueeze(0)          # (1, time)
        else:
            waveform = torch.from_numpy(samples.reshape(-1, n_channels).T)  # (C, time)
        audio_input = {"waveform": waveform, "sample_rate": sample_rate}
        raw = pipeline(audio_input, num_speakers=2)
        # Определяем тип вывода и извлекаем Annotation
        log.info(f"pyannote output type={type(raw).__name__}, "
                 f"attrs={[a for a in dir(raw) if not a.startswith('_')]}")
        if hasattr(raw, "itertracks"):
            annotation = raw
        elif hasattr(raw, "speaker_diarization"):   # pyannote ≥3.3 DiarizeOutput
            annotation = raw.speaker_diarization
        elif hasattr(raw, "diarization"):
            annotation = raw.diarization
        elif hasattr(raw, "annotation"):
            annotation = raw.annotation
        else:
            raise ValueError(f"Не удалось извлечь Annotation из {type(raw).__name__}: "
                             f"attrs={[a for a in dir(raw) if not a.startswith('_')]}")
        diar_segs: list[tuple[float, float, str]] = [
            (turn.start, turn.end, speaker)
            for turn, _, speaker in annotation.itertracks(yield_label=True)
        ]
        log.info(f"pyannote: {len(diar_segs)} диаризационных отрезков, "
                 f"уникальных спикеров: {len(set(s for _, _, s in diar_segs))}")
        return _merge_segments(whisper_segs, diar_segs)
    except Exception as e:
        log.error(f"Ошибка диаризации: {e} — возвращаем сегменты без ролей (spk0)")
        return whisper_segs


def _merge_segments(
    whisper_segs: list[RawSpeakerSegment],
    diar_segs: list[tuple[float, float, str]],
) -> list[RawSpeakerSegment]:
    """
    Сопоставляет whisper-сегменты с диаризационными отрезками по перекрытию.

    Нормализация меток: первый встреченный спикер (по времени) → spk0,
    второй → spk1. Это согласуется с RoleAssigner в Java-бэкенде, где
    spk0 считается интервьюером (он обычно говорит первым).
    """
    label_map: dict[str, str] = {}

    def normalize(label: str) -> str:
        if label not in label_map:
            label_map[label] = f"spk{len(label_map)}"
        return label_map[label]

    result: list[RawSpeakerSegment] = []
    for wseg in whisper_segs:
        w_start = wseg.startMs / 1000.0
        w_end   = wseg.endMs   / 1000.0

        best_speaker = "spk0"
        best_overlap = 0.0

        for d_start, d_end, d_label in diar_segs:
            overlap = max(0.0, min(w_end, d_end) - max(w_start, d_start))
            if overlap > best_overlap:
                best_overlap = overlap
                best_speaker = normalize(d_label)

        result.append(RawSpeakerSegment(
            rawSpeakerId=best_speaker,
            startMs=wseg.startMs,
            endMs=wseg.endMs,
            text=wseg.text,
            prosody=wseg.prosody,  # просодика не зависит от назначенной роли — сохраняем
        ))

    assigned = set(s.rawSpeakerId for s in result)
    log.info(f"Merge: {len(result)} сегментов назначены спикерам {assigned} "
             f"(label_map={label_map})")
    return result


# ──────────────────────────────────────────────────────────────────────────────
# ffmpeg-утилиты
# ──────────────────────────────────────────────────────────────────────────────

def _ffmpeg_to_wav(src: Path, dst: Path) -> None:
    """ffmpeg: конвертирует в WAV 16кГц, сохраняет исходное число каналов."""
    cmd = [
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-i", str(src),
        "-ar", "16000",
        "-vn", "-map_metadata", "-1",
        "-y", str(dst),
    ]
    try:
        subprocess.run(cmd, check=True, capture_output=True, text=True)
    except FileNotFoundError as e:
        raise HTTPException(500, "ffmpeg не найден в PATH — установите ffmpeg") from e
    except subprocess.CalledProcessError as e:
        log.error(f"ffmpeg упал: {e.stderr}")
        raise HTTPException(400, f"Не удалось декодировать файл: {e.stderr[:200]}") from e


def _wav_channels(path: Path) -> int:
    with wave.open(str(path), "rb") as w:
        return w.getnchannels()


def _channels_are_identical(wav_path: Path, threshold: float = 0.015) -> bool:
    """
    Возвращает True если L- и R-каналы фактически одинаковы (фейковое стерео).
    """
    try:
        import numpy as np

        with wave.open(str(wav_path), "rb") as w:
            n_frames = w.getnframes()
            if n_frames == 0:
                return True
            max_frames = min(n_frames, 480_000)  # 30 сек × 16000
            raw = w.readframes(max_frames)

        samples = np.frombuffer(raw, dtype=np.int16).astype(np.float32)
        if len(samples) % 2 != 0:
            samples = samples[:-1]
        stereo = samples.reshape(-1, 2)
        left, right = stereo[:, 0], stereo[:, 1]

        denom = max(np.abs(left).mean(), np.abs(right).mean(), 1.0)
        rel_diff = np.abs(left - right).mean() / denom
        log.info(f"Stereo channel diff: {rel_diff:.4f} (threshold={threshold})")
        return float(rel_diff) < threshold

    except Exception as e:
        log.warning(f"_channels_are_identical упал ({e}), считаем каналы разными")
        return False


def _stereo_to_mono(wav_path: Path, tmpdir: Path) -> Path:
    """Смешивает L+R в один канал через ffmpeg."""
    mono_path = tmpdir / "mono.wav"
    subprocess.run([
        "ffmpeg", "-hide_banner", "-loglevel", "error",
        "-i", str(wav_path),
        "-ac", "1",
        "-y", str(mono_path),
    ], check=True, capture_output=True)
    return mono_path


def get_app() -> FastAPI:
    return app
