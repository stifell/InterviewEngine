import { useState } from 'react';
import type { CompetencyEvaluation, IndicatorEvaluation } from '../api/types';

interface Props {
  competencies: CompetencyEvaluation[];
}

/**
 * Тепловая карта «приемлемо/нет» по индикаторам — закрывает требование R4
 * (бинарный вердикт по каждому индикатору, §1 CLAUDE.md). По клику на ячейку
 * показывает цитату-доказательство и rationale судьи (§8 «нет цитаты — нет баллов»).
 */
export function IndicatorHeatmap({ competencies }: Props) {
  const [selected, setSelected] = useState<{
    competencyTitle: string;
    indicator: IndicatorEvaluation;
  } | null>(null);

  return (
    <div className="bg-white rounded-lg shadow p-4 space-y-4">
      <div>
        <h3 className="text-lg font-semibold">Тепловая карта индикаторов</h3>
        <p className="text-xs text-slate-500">
          Зелёный = приемлемо (score ≥ acceptableFrom). Кликните по ячейке, чтобы увидеть цитату-доказательство.
        </p>
      </div>

      <div className="space-y-3">
        {competencies.map((c) => (
          <div key={c.competencyId}>
            <div className="flex items-baseline gap-2 mb-1">
              <span className="font-medium">{c.title}</span>
              <span className="text-xs text-slate-500">
                балл {c.score.toFixed(2)} / 5 · вес {c.weight.toFixed(2)}
              </span>
            </div>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {c.indicators.map((ind) => (
                <button
                  type="button"
                  key={ind.indicatorId}
                  onClick={() => setSelected({ competencyTitle: c.title, indicator: ind })}
                  className={`text-left rounded border px-3 py-2 text-xs hover:shadow ${
                    ind.acceptable
                      ? 'bg-emerald-50 border-emerald-300 text-emerald-900'
                      : 'bg-rose-50 border-rose-300 text-rose-900'
                  }`}
                  title={ind.rationale}
                >
                  <div className="font-mono truncate">{ind.indicatorId}</div>
                  <div className="font-semibold text-sm">
                    {ind.score} / 5 — {ind.acceptable ? '✓ приемлемо' : '✗ не пройдено'}
                  </div>
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>

      {selected && (
        <div className="mt-4 border-t pt-4 space-y-2">
          <div className="flex items-center justify-between">
            <h4 className="font-semibold">
              {selected.competencyTitle} → <span className="font-mono">{selected.indicator.indicatorId}</span>
            </h4>
            <button
              type="button"
              className="text-xs text-slate-500 hover:text-slate-700"
              onClick={() => setSelected(null)}
            >
              закрыть
            </button>
          </div>
          {selected.indicator.evidenceQuote ? (
            <blockquote className="border-l-4 border-slate-300 pl-3 text-sm italic text-slate-700">
              «{selected.indicator.evidenceQuote}»
            </blockquote>
          ) : (
            <div className="text-xs text-slate-500">
              Судья не нашёл подходящей цитаты — балл ограничен (§8 «нет доказательства — нет баллов»).
            </div>
          )}
          <p className="text-sm text-slate-800">{selected.indicator.rationale}</p>
        </div>
      )}
    </div>
  );
}
