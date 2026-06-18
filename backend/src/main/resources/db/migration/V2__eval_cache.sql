-- Кэш результатов оценки по каждому индикатору (§13 CLAUDE.md).
-- Хранит Map<indicatorId, IndicatorEvaluation> — повторный /evaluate не вызывает LLM
-- для индикаторов, которые уже были оценены в предыдущем запуске.
alter table interview add column if not exists eval_cache_json text;
