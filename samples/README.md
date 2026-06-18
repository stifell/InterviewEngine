# samples/ — тестовые транскрипты и аудио

Директория содержит образцы входных данных для ручного тестирования и демонстрации.
Реальные записи интервью с ФИО **не коммитить** — только синтетические фикстуры.

## Структура

```
samples/
  transcripts/        # готовые JSON-транскрипты (POST /api/interviews)
    senior-go-001.json
  audio/              # аудио/видео для POST /api/interviews/from-audio
    .gitkeep          # реальные файлы слишком большие — добавьте вручную
```

## Как использовать

### 1. Создать интервью из JSON-транскрипта

```bash
curl -s -X POST http://localhost:8080/api/interviews \
  -H "Content-Type: application/json" \
  -d @samples/transcripts/senior-go-001.json | jq .
```

Ответ: `{ "id": "<uuid>", "status": "PENDING" }`

### 2. Запустить оценку

```bash
INTERVIEW_ID=<uuid>
curl -s -X POST http://localhost:8080/api/interviews/$INTERVIEW_ID/evaluate | jq .
```

### 3. Дождаться результата и запросить скоркарту

```bash
# Проверить статус (PENDING → RUNNING → DONE)
curl -s http://localhost:8080/api/interviews/$INTERVIEW_ID | jq .status

# Получить результат
curl -s http://localhost:8080/api/interviews/$INTERVIEW_ID/result | jq .
```

### 4. Загрузить аудио (нужен запущенный sidecar)

```bash
curl -s -X POST http://localhost:8080/api/interviews/from-audio \
  -F "file=@samples/audio/your-interview.mp4" \
  -F "position=senior-go-developer" | jq .
```

## Описание фикстур

### `transcripts/senior-go-001.json`

Синтетическое интервью на позицию **Senior Go Developer** (~15 минут, 4 блока).
Кандидат «Алексей Смирнов» (вымышленный). Намеренно демонстрирует:
- сильный Ownership (блок 2): чёткие «я сделал», конкретные цифры
- среднюю Concurrency (блок 3): упоминает горутины, но без глубины в select/scheduler
- слабый Result (блок 2): размытые формулировки без метрик

Ожидаемый вердикт: **Hire** (не Strong, так как Concurrency на грани).

## Добавление новых фикстур

1. Создайте `transcripts/<имя>.json` по схеме `senior-go-001.json`.
2. Убедитесь, что `position` совпадает с именем файла в `resources/rubrics/`.
3. Запустите оценку и проверьте, что скоркарта соответствует ожидаемому вердикту.
