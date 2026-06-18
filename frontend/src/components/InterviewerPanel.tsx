import type {
  InterviewerEvaluation,
  NeutralityFlagKind,
  TimingDeviation,
} from '../api/types';

const FLAG_LABELS: Record<NeutralityFlagKind, string> = {
  SUGGESTIVE_AGREEMENT: 'Давление на согласие',
  ANSWER_REFRAMING: 'Перефразирует ответ',
  ANSWER_HINT: 'Подсказка ответа',
};

const FLAG_STYLES: Record<NeutralityFlagKind, string> = {
  SUGGESTIVE_AGREEMENT: 'bg-amber-50 border-amber-300 text-amber-900',
  ANSWER_REFRAMING: 'bg-orange-50 border-orange-300 text-orange-900',
  ANSWER_HINT: 'bg-rose-50 border-rose-300 text-rose-900',
};

interface Props {
  evaluation: InterviewerEvaluation;
}

export function InterviewerPanel({ evaluation }: Props) {
  const { mainQuestionCoverage, probeCoverage, scriptAdherence, missedProbeIds, neutralityFlags, timingDeviations } =
    evaluation;

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <h3 className="text-lg font-semibold">Оценка интервьюера</h3>

      <dl className="text-sm grid grid-cols-1 md:grid-cols-3 gap-2">
        <Stat label="Покрытие основных вопросов" value={percent(mainQuestionCoverage)} />
        <Stat label="Покрытие проб" value={percent(probeCoverage)} />
        <Stat label="Соответствие скрипту" value={percent(scriptAdherence)} />
      </dl>

      {missedProbeIds.length > 0 && (
        <Section title="Пропущенные пробы">
          <div className="text-xs font-mono text-slate-700">{missedProbeIds.join(', ')}</div>
        </Section>
      )}

      <Section title="Замечания по нейтральности">
        {neutralityFlags.length === 0 ? (
          <div className="text-xs text-slate-500">Не обнаружено наводящих формулировок.</div>
        ) : (
          <ul className="space-y-2">
            {neutralityFlags.map((f, idx) => (
              <li key={idx} className={`text-xs rounded border px-3 py-2 ${FLAG_STYLES[f.kind]}`}>
                <div className="flex items-baseline justify-between gap-2">
                  <span className="font-semibold">{FLAG_LABELS[f.kind]}</span>
                  <span className="font-mono text-[10px] opacity-70">{f.blockId} · «{f.trigger}»</span>
                </div>
                <blockquote className="mt-1 italic">«{f.quote}»</blockquote>
              </li>
            ))}
          </ul>
        )}
      </Section>

      <Section title="Тайминг">
        {timingDeviations.length === 0 ? (
          <div className="text-xs text-slate-500">
            Нет таймстемпов в транскрипте — тайминг не оценивается.
          </div>
        ) : (
          <ul className="space-y-1 text-xs">
            {timingDeviations.map((d) => (
              <li key={d.blockId} className="flex justify-between font-mono">
                <span>{d.blockId}</span>
                <span>
                  {d.actualMinutes.toFixed(1)} мин / план {d.plannedMinutes} мин · {formatDeviation(d)}
                </span>
              </li>
            ))}
          </ul>
        )}
      </Section>
    </div>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="border rounded px-3 py-2">
      <dt className="text-[11px] text-slate-500 uppercase tracking-wide">{label}</dt>
      <dd className="text-lg font-semibold font-mono">{value}</dd>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <h4 className="text-sm font-semibold mb-1">{title}</h4>
      {children}
    </div>
  );
}

function percent(v: number): string {
  return `${(v * 100).toFixed(0)}%`;
}

function formatDeviation(d: TimingDeviation): string {
  const sign = d.deviationMinutes > 0 ? '+' : '';
  const cls = Math.abs(d.deviationMinutes) <= 1 ? '' : '⚠';
  return `${sign}${d.deviationMinutes.toFixed(1)} мин ${cls}`.trim();
}
