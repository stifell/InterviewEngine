import type { Recommendation } from '../api/types';

const STYLES: Record<Recommendation, { label: string; classes: string }> = {
  STRONG_HIRE: { label: 'Strong Hire', classes: 'bg-emerald-100 text-emerald-800 border-emerald-300' },
  HIRE: { label: 'Hire', classes: 'bg-sky-100 text-sky-800 border-sky-300' },
  NO_HIRE: { label: 'No Hire', classes: 'bg-rose-100 text-rose-800 border-rose-300' },
};

export function RecommendationBadge({ value }: { value: Recommendation }) {
  const s = STYLES[value];
  return (
    <span
      className={`inline-block px-3 py-1 rounded-full border text-sm font-semibold ${s.classes}`}
    >
      {s.label}
    </span>
  );
}
