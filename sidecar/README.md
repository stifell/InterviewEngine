# Sidecar транскрипции

Python-сервис для русскоязычной ASR. Используется бэкендом через
[`SidecarTranscriber`](../backend/src/main/java/com/interviewengine/ingest/SidecarTranscriber.java)
по HTTP. Поднимается рядом с бэком и автоматически подключается, если
в конфиге задан `transcriber.sidecar.url`.

## Что внутри

- **FastAPI** — HTTP-обвязка
- **ffmpeg** — нормализация аудио в WAV 16кГц (берётся из системы)
- **faster-whisper** (модель `large-v3-turbo` по умолчанию) — ASR
  с встроенным VAD (Voice Activity Detection) для сегментации
- **Praat / parselmouth** — извлечение просодики (темп, паузы, интонация F0,
  громкость) по каждому сегменту; см. [`prosody.py`](prosody.py). Если библиотека не
  установлена, поле `prosody` приходит `null`, остальное работает как прежде
- **Stereo-канальный режим** для диаризации — если запись на 2 канала
  (channel 0 = интервьюер, channel 1 = кандидат), каналы транскрибируются
  по отдельности и объединяются по таймкодам. Это §5.1 «лайфхак записи»
  CLAUDE.md и **надёжнее любой автоматической диаризации**

## Что **не** в v1

Автоматическая диаризация моно-записей через `pyannote.audio` — требует
HuggingFace токен и условия использования модели
`pyannote/speaker-diarization-3.1`. Для моно сейчас все сегменты помечаются
одним `spk0` — для оценки это работает только если есть один говорящий
или известно, что моно → роль `CANDIDATE` (что и делает
[`RoleAssigner`](../backend/src/main/java/com/interviewengine/ingest/RoleAssigner.java)).

Реальный production-кейс — писать интервьюера и кандидата на раздельные
каналы (отдельные микрофоны или per-participant запись в созвоне Zoom/Meet/Teams).

## Контракт API

```
POST /transcribe
Content-Type: multipart/form-data
file=<media bytes>

→ 200 OK
{
  "segments": [
    {
      "rawSpeakerId": "spk0", "startMs": 0, "endMs": 4500, "text": "Здравствуйте...",
      "prosody": {
        "speechRateWpm": 132.0, "articulationRateWpm": 160.0,
        "pauseRatio": 0.18, "pauseCount": 4, "meanPauseMs": 420.0,
        "pitchMeanHz": 150.0, "pitchVariationSemitones": 3.1,
        "intensityMeanDb": 62.0, "intensityVariationDb": 5.0,
        "voicedRatio": 0.8, "durationMs": 4500
      }
    },
    {"rawSpeakerId": "spk1", "startMs": 4600, "endMs": 12300, "text": "Привет...", "prosody": null}
  ]
}
```

Поле `prosody` равно `null`, если `praat-parselmouth` не установлен или сегмент
слишком короткий для анализа. Формат совпадает с Java-record
`com.interviewengine.domain.ProsodicFeatures`.

`spk0` всегда соответствует первому/левому каналу, `spk1` — второму/правому.
В моно-режиме всегда только `spk0`.

```
GET /health → {"status": "ok"}
```

## Запуск

### Первый раз

```bash
cd sidecar
python -m venv .venv

# Windows PowerShell:
.venv\Scripts\Activate.ps1
# или Git Bash:
source .venv/Scripts/activate

.venv/Scripts/python.exe -m pip install --upgrade pip
.venv/Scripts/python.exe -m pip install -r requirements.txt
```

Установка тянет ~1.5 ГБ зависимостей (torch не нужен — `faster-whisper`
использует ctranslate2). Первый запуск ещё скачает whisper-модель ~800МБ
из HuggingFace, дальше она кешируется.

### Регулярный запуск

```bash
.venv/Scripts/python.exe -m uvicorn main:app --host 127.0.0.1 --port 8001
```

И в [`backend/src/main/resources/application-local.yaml`](../backend/src/main/resources/application-local.yaml):

```yaml
transcriber:
  sidecar:
    url: http://127.0.0.1:8001
```

Бэк автоматически подцепит `SidecarTranscriber` вместо `GeminiTranscriber`
(см. `@ConditionalOnProperty + @Primary` на классе).

## Переменные окружения

| Переменная | По умолчанию | Что меняет |
|------------|--------------|-----------|
| `WHISPER_MODEL` | `large-v3-turbo` | размер модели: `tiny`, `base`, `small`, `medium`, `large-v3`, `large-v3-turbo` |
| `WHISPER_DEVICE` | `cpu` | `cuda` если есть NVIDIA GPU с CUDA |
| `WHISPER_COMPUTE_TYPE` | `int8` (cpu) / `float16` (gpu) | trade-off скорость/качество |

Для очень слабых машин: `WHISPER_MODEL=small` — модель ~250МБ, в разы быстрее,
русский качается хуже, но для prototyping ok.

## Проверка вручную

```bash
curl http://127.0.0.1:8001/health
# {"status":"ok"}

curl -X POST http://127.0.0.1:8001/transcribe -F "file=@/path/to/sample.mp3"
# {"segments":[{"rawSpeakerId":"spk0","startMs":340,"endMs":4520,"text":"..."}, ...]}
```

Первый POST после старта займёт ~30–60 секунд (модель загружается в память
лениво). Последующие — ~1× длительности записи на CPU с большой моделью,
~0.3× на GPU.

## Производительность

| Длительность записи | Модель | CPU (8 core) | GPU (RTX) |
|---|---|---|---|
| 1 мин | large-v3-turbo | ~30 сек | ~5 сек |
| 30 мин | large-v3-turbo | ~15 мин | ~2 мин |
| 30 мин | small | ~3 мин | ~30 сек |

Если 30-минутное интервью на CPU превращается в 15 минут ожидания — это
нормально, асинхронный конвейер всё равно отдаст результат в UI, когда
готово. Для регулярного использования стоит подумать про GPU или модель
поменьше.

## Что в Sidecar **не делает**

- **Не диаризирует моно-запись**. Для этого нужна `pyannote.audio` с
  HF-токеном — добавится в v2, или используйте stereo-режим уже сейчас.
- **Не определяет роли** «интервьюер / кандидат». Это делает Java
  [`RoleAssigner`](../backend/src/main/java/com/interviewengine/ingest/RoleAssigner.java)
  на основе jaccard-overlap с шаблонными `mainQuestion`. Sidecar только
  выдаёт нумерованные кластеры спикеров.
- **Не привязывает к блокам интервью**. Это шаг 3 в Java-конвейере
  ([`HeuristicBlockClassifier`](../backend/src/main/java/com/interviewengine/ingest/HeuristicBlockClassifier.java)).

Это сознательное разделение: всё, что про текст и логику оценки — в Java,
sidecar занят исключительно тяжёлым ML.
