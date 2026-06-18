# Движок автоматической оценки интервью

> **© 2026 Denis Matyushkin ([github.com/stifell](https://github.com/stifell)). Все права защищены.**
> Проприетарное ПО. Автор — единственный правообладатель. Любое использование, копирование,
> изменение, распространение или коммерциализация (в т.ч. передача третьим лицам) **без
> письменного разрешения автора запрещены** — см. [`LICENSE`](LICENSE) и [`NOTICE`](NOTICE).
> Размещение в публичном доступе не передаёт никаких прав. Контакт: semj6822@gmail.com

Курсовой проект: система, которая автоматически оценивает **структурированные собеседования**
по их транскриптам. На выход — скоркарта кандидата, оценка интервьюера и рекомендация
(Strong Hire / Hire / No Hire).

---

## Структура репозитория

```
backend/      # Java 21 + Spring Boot 3.5 + Spring AI → Gemini 2.5 Flash
frontend/     # React 19 + Vite + TypeScript + Tailwind 4 + Recharts
sidecar/      # Python 3 + FastAPI + faster-whisper (ASR) + Praat/parselmouth (просодика)
samples/      # Образцы аудио/транскриптов для тестирования
```

---

## Быстрый старт (локально, без Docker)

### Предварительно

- Java 21+, Maven
- Node.js 20+
- Python 3.10+ (для сайдкара)
- ffmpeg в PATH
- Ключ Gemini AI Studio: <https://aistudio.google.com/> → Create API Key

### 1. Бэкенд

```powershell
cd backend
$env:GEMINI_API_KEY = "ваш-ключ"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
```

Бэкенд стартует на `http://localhost:8080`.

> **Профиль `local`** использует H2 in-memory вместо PostgreSQL — ничего дополнительно
> устанавливать не нужно. Для PostgreSQL — см. ниже раздел Docker.

### 2. Фронтенд

```powershell
cd frontend
npm install
npm run dev
```

Фронт доступен на `http://localhost:5173`.

### 3. Python-сайдкар (опционально — для транскрипции аудио)

Без сайдкара транскрипция работает через Gemini multimodal (менее точная диаризация,
но не требует Python).

```powershell
cd sidecar
python -m venv .venv
.venv\Scripts\Activate.ps1
.venv\Scripts\python.exe -m pip install -r requirements.txt

# Запуск:
.venv\Scripts\python.exe -m uvicorn main:app --host 127.0.0.1 --port 8001
```

После запуска сайдкар подхватывается автоматически через `transcriber.sidecar.url`
в `application-local.yaml`. Подробности — в [`sidecar/README.md`](sidecar/README.md).

---

## Тесты

```powershell
cd backend
./mvnw test
```

70 тестов; 68 проходят без сети, 2 — интеграционные с Gemini (пропускаются
без `GEMINI_API_KEY`).

---

## Docker (PostgreSQL + бэкенд)

> Docker-compose находится в разработке. Пока — запуск в профиле `local` с H2
> (см. «Быстрый старт» выше).

---

## API

| Метод | Путь | Описание |
|-------|------|----------|
| `POST` | `/api/interviews` | Создать интервью из текстового транскрипта |
| `POST` | `/api/interviews/from-audio` | Загрузить аудио/видео файл (multipart) |
| `GET` | `/api/interviews/{id}` | Статус + сырой транскрипт |
| `POST` | `/api/interviews/{id}/evaluate` | Запустить оценку (асинхронно) |
| `GET` | `/api/interviews/{id}/result` | Скоркарта + оценка интервьюера |
| `GET` | `/api/rubrics` | Список шаблонов позиций |
| `GET` | `/api/rubrics/{position}` | Детали рубрикатора |

---

## Технологический стек

| Слой | Технология |
|------|-----------|
| Бэкенд | Java 21, Spring Boot 3.5, Spring AI 1.1, JPA + Flyway |
| БД | PostgreSQL (прод) / H2 (dev) |
| LLM | Google Gemini 2.5 Flash (через Spring AI) |
| Транскрипция | faster-whisper large-v3-turbo (Python sidecar) или Gemini multimodal |
| Признаки речи | лексика (Java, R4) + просодика (Praat/parselmouth в сайдкаре) |
| Фронтенд | React 19, Vite 6, TypeScript strict, Tailwind 4, Recharts |

---

## Переменные окружения

| Переменная | Где нужна | Описание |
|-----------|-----------|----------|
| `GEMINI_API_KEY` | бэкенд | Ключ Google AI Studio |
| `WHISPER_MODEL` | sidecar | Размер модели (по умолчанию `large-v3-turbo`) |
| `WHISPER_DEVICE` | sidecar | `cpu` или `cuda` |
