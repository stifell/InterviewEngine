import { useEffect, useState } from 'react';
import { api } from '../api/client';
import type { TranscriptSegment } from '../api/types';

interface Props {
  onSubmitted: (interviewId: string) => void;
}

type Mode = 'json' | 'audio';

const SAMPLE_JSON = `[
  {"speaker": "INTERVIEWER", "blockId": "block1", "text": "Расскажите коротко об опыте."},
  {"speaker": "CANDIDATE", "blockId": "block1", "text": "Я работаю на Go 6 лет, я писал биллинг."},
  {"speaker": "INTERVIEWER", "blockId": "block1", "text": "Какова ваша личная роль?"},
  {"speaker": "CANDIDATE", "blockId": "block1", "text": "Я был техлидом, отвечал за архитектуру."}
]`;

export function TranscriptUploader({ onSubmitted }: Props) {
  const [positions, setPositions] = useState<string[]>([]);
  const [position, setPosition] = useState<string>('');
  const [mode, setMode] = useState<Mode>('json');
  const [raw, setRaw] = useState<string>(SAMPLE_JSON);
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api
      .listRubrics()
      .then((list) => {
        setPositions(list);
        if (list.length > 0) setPosition(list[0]);
      })
      .catch((e: Error) => setError(`Не удалось загрузить список рубрикаторов: ${e.message}`));
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'json') {
        await submitJson();
      } else {
        await submitAudio();
      }
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function submitJson() {
    let segments: TranscriptSegment[];
    try {
      segments = JSON.parse(raw) as TranscriptSegment[];
      if (!Array.isArray(segments) || segments.length === 0) {
        throw new Error('Транскрипт должен быть непустым массивом сегментов.');
      }
    } catch (err) {
      throw new Error(`Невалидный JSON: ${(err as Error).message}`);
    }
    const created = await api.createInterview(position, segments);
    await api.startEvaluation(created.id);
    onSubmitted(created.id);
  }

  async function submitAudio() {
    if (!file) {
      throw new Error('Выберите файл записи');
    }
    const created = await api.createInterviewFromAudio(position, file);
    // оценка запустится автоматически после транскрипции (AsyncTranscriber → AsyncEvaluator)
    onSubmitted(created.interviewId);
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4 p-6 bg-white rounded-lg shadow">
      <h2 className="text-xl font-semibold">Новое интервью</h2>

      <div>
        <label className="block text-sm font-medium mb-1">Позиция (рубрикатор)</label>
        <select
          className="w-full border rounded px-3 py-2"
          value={position}
          onChange={(e) => setPosition(e.target.value)}
          disabled={busy || positions.length === 0}
        >
          {positions.map((p) => (
            <option key={p} value={p}>
              {p}
            </option>
          ))}
        </select>
      </div>

      <div className="flex gap-1 border-b border-slate-200">
        <ModeTab active={mode === 'json'} onClick={() => setMode('json')} disabled={busy}>
          JSON-транскрипт
        </ModeTab>
        <ModeTab active={mode === 'audio'} onClick={() => setMode('audio')} disabled={busy}>
          Аудио / видео
        </ModeTab>
      </div>

      {mode === 'json' && (
        <div>
          <label className="block text-sm font-medium mb-1">
            JSON-массив сегментов с полями speaker / blockId / text
          </label>
          <textarea
            className="w-full font-mono text-sm border rounded px-3 py-2 h-64"
            value={raw}
            onChange={(e) => setRaw(e.target.value)}
            disabled={busy}
            spellCheck={false}
          />
          <p className="text-xs text-slate-500 mt-1">
            Поля <code>startMs</code> / <code>endMs</code> опциональны — если есть, оценка интервьюера
            посчитает тайминг блоков.
          </p>
        </div>
      )}

      {mode === 'audio' && (
        <div>
          <label className="block text-sm font-medium mb-1">Файл записи (audio/* или video/*)</label>
          <input
            type="file"
            accept="audio/*,video/*"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            disabled={busy}
            className="block w-full text-sm text-slate-700
                       file:mr-3 file:py-2 file:px-4
                       file:rounded file:border file:border-slate-300
                       file:bg-slate-50 hover:file:bg-slate-100
                       file:cursor-pointer cursor-pointer"
          />
          {file && (
            <p className="text-xs text-slate-600 mt-2 font-mono">
              {file.name} · {(file.size / (1024 * 1024)).toFixed(2)} МБ · {file.type || 'unknown'}
            </p>
          )}
          <p className="text-xs text-slate-500 mt-2">
            Бэкенд расшифрует запись (Gemini multimodal или Python-сайдкар), назначит роли
            и блоки автоматически, потом запустит оценку. Это занимает 30–90 секунд в зависимости
            от длительности записи.
          </p>
        </div>
      )}

      {error && (
        <div className="text-sm text-red-700 bg-red-50 border border-red-200 rounded px-3 py-2 whitespace-pre-wrap">
          {error}
        </div>
      )}

      <button
        type="submit"
        className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-slate-400 text-white rounded font-medium"
        disabled={busy || !position || (mode === 'audio' && !file)}
      >
        {busy ? 'Отправляю…' : 'Загрузить и оценить'}
      </button>
    </form>
  );
}

function ModeTab({
  active,
  onClick,
  disabled,
  children,
}: {
  active: boolean;
  onClick: () => void;
  disabled?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition ${
        active
          ? 'border-blue-600 text-blue-700'
          : 'border-transparent text-slate-500 hover:text-slate-700'
      } disabled:opacity-50`}
    >
      {children}
    </button>
  );
}
