# Python 3.11 — стабильная версия с wheels для faster-whisper и ctranslate2
FROM python:3.11-slim

# ffmpeg нужен для конвертации аудио в WAV 16 кГц
RUN apt-get update && apt-get install -y --no-install-recommends \
    ffmpeg \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY main.py .

EXPOSE 8001

# Модель скачается лениво при первом /transcribe — том whisper-cache сохраняет её
CMD ["python", "-m", "uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8001"]
