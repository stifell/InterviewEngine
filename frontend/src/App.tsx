import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from './api/client';
import type { EvaluationResult, InterviewStatus, ModelInfo, TranscriptSegment } from './api/types';
import { ScorecardView } from './components/ScorecardView';
import { TranscriptUploader } from './components/TranscriptUploader';

type Stage =
  | { kind: 'idle' }
  | { kind: 'evaluating'; interviewId: string; status: InterviewStatus }
  | { kind: 'done'; interviewId: string; result: EvaluationResult; segments: TranscriptSegment[] }
  | { kind: 'failed'; interviewId: string; message: string };

const POLL_INTERVAL_MS = 1000;

export default function App() {
  const [stage, setStage] = useState<Stage>({ kind: 'idle' });
  const [currentModel, setCurrentModel] = useState<string>('');
  const [availableModels, setAvailableModels] = useState<ModelInfo[]>([]);

  // Загружаем настройки при старте
  useEffect(() => {
    api.getSettings().then((s) => {
      setCurrentModel(s.currentModel);
      setAvailableModels(s.availableModels);
    }).catch((err) => console.warn('GET /api/settings failed:', err));
  }, []);

  // Опрашиваем бэкенд, пока интервью не уйдёт в DONE или FAILED.
  useEffect(() => {
    if (stage.kind !== 'evaluating') return;
    const interviewId = stage.interviewId;

    let cancelled = false;
    const tick = async () => {
      try {
        const view = await api.getInterview(interviewId);
        if (cancelled) return;
        if (view.status === 'DONE') {
          const result = await api.getResult(interviewId);
          if (cancelled) return;
          if (result) {
            setStage({ kind: 'done', interviewId, result, segments: view.segments });
            return;
          }
        }
        if (view.status === 'FAILED') {
          setStage({
            kind: 'failed',
            interviewId,
            message: view.errorMessage ?? 'Оценка завершилась ошибкой',
          });
          return;
        }
        setStage((prev) =>
          prev.kind === 'evaluating' && prev.interviewId === interviewId
            ? { ...prev, status: view.status }
            : prev,
        );
      } catch (err) {
        if (!cancelled) {
          setStage({
            kind: 'failed',
            interviewId,
            message: err instanceof ApiError ? err.message : (err as Error).message,
          });
        }
      }
    };
    const id = window.setInterval(tick, POLL_INTERVAL_MS);
    void tick();
    return () => {
      cancelled = true;
      window.clearInterval(id);
    };
  }, [stage]);

  return (
    <div className="min-h-full flex flex-col">
      <header className="bg-slate-900 text-white px-6 py-4 shadow">
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <h1 className="text-lg font-semibold">Interview Engine — оценка интервью</h1>
            <p className="text-xs text-slate-300">
              Структурированные собеседования. Скоркарта · радар · тепловая карта приемлемо/нет.
            </p>
          </div>
          {availableModels.length > 0 && (
            <ModelSwitcher
              currentModel={currentModel}
              availableModels={availableModels}
              onChange={(m) => setCurrentModel(m)}
            />
          )}
        </div>
      </header>

      <main className="flex-1 max-w-5xl mx-auto w-full p-6 space-y-4">
        {stage.kind === 'idle' && (
          <>
            <TranscriptUploader
              onSubmitted={(id) =>
                setStage({ kind: 'evaluating', interviewId: id, status: 'PENDING' })
              }
            />
            <LoadByUuid onLoaded={(id, result, segments) => setStage({ kind: 'done', interviewId: id, result, segments })} />
          </>
        )}

        {stage.kind === 'evaluating' && (
          <div className="bg-white rounded-lg shadow p-6 text-center">
            <div className="text-lg font-semibold mb-2">Оценка идёт…</div>
            <div className="text-sm text-slate-500 font-mono">
              интервью {stage.interviewId} · статус {stage.status}
            </div>
            <p className="text-xs text-slate-400 mt-4">
              Конвейер обращается к LLM-судье по каждому индикатору. Это занимает обычно 10–30 секунд.
            </p>
          </div>
        )}

        {stage.kind === 'done' && (
          <ScorecardView
            result={stage.result}
            segments={stage.segments}
            onReset={() => setStage({ kind: 'idle' })}
          />
        )}

        {stage.kind === 'failed' && (
          <div className="bg-rose-50 border border-rose-200 rounded-lg p-6 space-y-3">
            <div className="text-rose-900 font-semibold">Оценка завершилась с ошибкой</div>
            <pre className="text-xs whitespace-pre-wrap text-rose-800">{stage.message}</pre>
            <button
              type="button"
              className="px-3 py-1 bg-rose-600 hover:bg-rose-700 text-white text-sm rounded"
              onClick={() => setStage({ kind: 'idle' })}
            >
              Попробовать снова
            </button>
          </div>
        )}
      </main>
    </div>
  );
}

// ── Переключатель LLM-модели ───────────────────────────────────────────────────
function ModelSwitcher({
  currentModel,
  availableModels,
  onChange,
}: {
  currentModel: string;
  availableModels: ModelInfo[];
  onChange: (model: string) => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleChange(e: React.ChangeEvent<HTMLSelectElement>) {
    const model = e.target.value;
    setError(null);
    setBusy(true);
    try {
      const s = await api.setModel(model);
      onChange(s.currentModel);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const info = availableModels.find((m) => m.id === currentModel);

  return (
    <div className="flex flex-col items-end gap-1 shrink-0">
      <div className="flex items-center gap-2">
        <label className="text-xs text-slate-400 whitespace-nowrap">LLM-модель:</label>
        <select
          value={currentModel}
          onChange={handleChange}
          disabled={busy}
          className="text-xs rounded px-2 py-1 bg-slate-700 text-white border border-slate-600 disabled:opacity-60 cursor-pointer"
        >
          {availableModels.map((m) => (
            <option key={m.id} value={m.id}>
              {m.displayName}
            </option>
          ))}
        </select>
      </div>
      {info && info.inputTokenLimit > 0 && (
        <p className="text-[11px] text-slate-400">
          контекст {(info.inputTokenLimit / 1000).toLocaleString()}K токенов
          · лимиты RPM/RPD — в AI Studio
        </p>
      )}
      {error && <p className="text-[11px] text-red-400">{error}</p>}
    </div>
  );
}

// ── Загрузить результат по UUID (для интервью, созданных через curl) ──────────
function LoadByUuid({
  onLoaded,
}: {
  onLoaded: (id: string, result: EvaluationResult, segments: TranscriptSegment[]) => void;
}) {
  const [uuid, setUuid] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  async function handleLoad(e: React.FormEvent) {
    e.preventDefault();
    const id = uuid.trim();
    if (!id) return;
    setError(null);
    setBusy(true);
    try {
      const view = await api.getInterview(id);
      if (view.status !== 'DONE') {
        setError(`Интервью ${id} ещё не оценено (статус: ${view.status})`);
        return;
      }
      const result = await api.getResult(id);
      if (!result) {
        setError('Результат не найден — возможно, оценка ещё не завершена');
        return;
      }
      onLoaded(id, result, view.segments);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : (err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form
      onSubmit={handleLoad}
      className="bg-white rounded-lg shadow p-6 space-y-3 border-t-4 border-slate-300"
    >
      <p className="text-sm font-medium text-slate-600">
        Или загрузите результат уже существующего интервью по UUID
      </p>
      <div className="flex gap-2">
        <input
          ref={inputRef}
          type="text"
          className="flex-1 border rounded px-3 py-2 font-mono text-sm"
          placeholder="8e6aee5d-6245-4769-8d8a-be4436b60ace"
          value={uuid}
          onChange={(e) => setUuid(e.target.value)}
          disabled={busy}
        />
        <button
          type="submit"
          disabled={busy || !uuid.trim()}
          className="px-4 py-2 bg-slate-700 hover:bg-slate-800 disabled:bg-slate-400 text-white rounded text-sm font-medium"
        >
          {busy ? 'Загружаю…' : 'Показать'}
        </button>
      </div>
      {error && (
        <p className="text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2">
          {error}
        </p>
      )}
    </form>
  );
}
