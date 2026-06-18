import type {
  EvaluationResult,
  EvaluationStartedResponse,
  InterviewView,
  Rubric,
  SettingsView,
  TranscriptSegment,
} from './types';

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      ...init?.headers,
    },
  });
  if (!res.ok) {
    const text = await res.text();
    throw new ApiError(res.status, text || res.statusText);
  }
  return (await res.json()) as T;
}

export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
    this.name = 'ApiError';
  }
}

export const api = {
  listRubrics: () => http<string[]>('/api/rubrics'),

  getRubric: (position: string) => http<Rubric>(`/api/rubrics/${encodeURIComponent(position)}`),

  createInterview: (position: string, segments: TranscriptSegment[]) =>
    http<InterviewView>('/api/interviews', {
      method: 'POST',
      body: JSON.stringify({ position, segments }),
    }),

  /**
   * Загружает аудио/видео для интервью. Бэкенд асинхронно расшифровывает
   * через {@code GeminiTranscriber} или {@code SidecarTranscriber}, после
   * чего сам запускает оценочный конвейер. Клиенту достаточно поллить
   * статус интервью.
   */
  async createInterviewFromAudio(position: string, file: File): Promise<{ interviewId: string; status: string }> {
    const form = new FormData();
    form.append('position', position);
    form.append('media', file);
    const res = await fetch('/api/interviews/from-audio', {
      method: 'POST',
      body: form,
      // НЕ задаём Content-Type вручную — браузер сам выставит multipart с boundary
    });
    if (!res.ok) {
      throw new ApiError(res.status, await res.text());
    }
    return (await res.json()) as { interviewId: string; status: string };
  },

  getInterview: (id: string) => http<InterviewView>(`/api/interviews/${id}`),

  getSettings: () => http<SettingsView>('/api/settings'),

  setModel: (model: string) =>
    http<SettingsView>('/api/settings/model', {
      method: 'PUT',
      body: JSON.stringify({ model }),
    }),

  /** Сбрасывает кэш моделей — бэкенд перезапросит Gemini API. */
  refreshModels: () => http<SettingsView>('/api/settings/models/refresh', { method: 'POST' }),

  startEvaluation: (id: string) =>
    http<EvaluationStartedResponse>(`/api/interviews/${id}/evaluate`, { method: 'POST' }),

  /**
   * Возвращает результат или null, если ещё не готов (бэк отдаёт 409 в этом случае).
   * Другие ошибки пробрасываются как {@link ApiError}.
   */
  async getResult(id: string): Promise<EvaluationResult | null> {
    const res = await fetch(`/api/interviews/${id}/result`, {
      headers: { Accept: 'application/json' },
    });
    if (res.status === 409) return null;
    if (!res.ok) throw new ApiError(res.status, await res.text());
    return (await res.json()) as EvaluationResult;
  },
};
