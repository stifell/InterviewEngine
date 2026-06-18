import type { AnswerFeatureView, LinguisticFeatures, ProsodicFeatures } from '../api/types';

interface Props {
  features: AnswerFeatureView[];
}

type Layer = 'lexical' | 'prosodic' | 'llm';

/** Метаданные сигналов рубрикатора: к какому слою относится и что измеряет. */
const SIGNAL_META: Record<string, { label: string; layer: Layer }> = {
  ownershipRatio: { label: 'ownershipRatio — доля «я» среди 1-го лица', layer: 'lexical' },
  answerLength: { label: 'answerLength — длина ответа', layer: 'lexical' },
  fillerDensity: { label: 'fillerDensity — слова-паразиты', layer: 'lexical' },
  hedgingDensity: { label: 'hedgingDensity — неуверенные обороты', layer: 'lexical' },
  hasMetrics: { label: 'hasMetrics — есть ли цифры/единицы', layer: 'lexical' },
  lexicalDiversity: { label: 'lexicalDiversity — богатство словаря (TTR)', layer: 'lexical' },
  termCoverage: { label: 'termCoverage — покрытие доменных терминов', layer: 'lexical' },
  starComponents: { label: 'starComponents — компоненты S/T/A/R', layer: 'llm' },
  speechRate: { label: 'speechRate — темп речи', layer: 'prosodic' },
  pauseRatio: { label: 'pauseRatio — доля пауз', layer: 'prosodic' },
  meanPauseMs: { label: 'meanPauseMs — средняя пауза', layer: 'prosodic' },
  pitchVariationSemitones: { label: 'pitchVariation — выразительность интонации', layer: 'prosodic' },
  intensityVariationDb: { label: 'intensityVariation — динамика громкости', layer: 'prosodic' },
};

const LAYER_STYLE: Record<Layer, string> = {
  lexical: 'bg-emerald-100 text-emerald-800',
  prosodic: 'bg-indigo-100 text-indigo-800',
  llm: 'bg-amber-100 text-amber-800',
};

function fmt(x: number, digits = 2): string {
  return x.toLocaleString('ru-RU', { maximumFractionDigits: digits });
}

function lexicalRows(f: LinguisticFeatures): Array<[string, string]> {
  return [
    ['Длина ответа', `${f.answerLength} слов`],
    ['ownershipRatio', f.hasFirstPerson ? fmt(f.ownershipRatio) : '— (нет 1-го лица)'],
    ['Слова-паразиты', `${fmt(f.fillerDensity)} /100 слов`],
    ['Неуверенность (hedging)', `${fmt(f.hedgingDensity)} /100 слов`],
    ['Есть метрики', f.hasMetrics ? 'да' : 'нет'],
    ['Разнообразие (TTR)', fmt(f.lexicalDiversity)],
  ];
}

function prosodicRows(p: ProsodicFeatures): Array<[string, string]> {
  return [
    ['Темп речи', `${fmt(p.speechRateWpm, 0)} слов/мин`],
    ['Темп артикуляции', `${fmt(p.articulationRateWpm, 0)} слов/мин`],
    ['Доля пауз', fmt(p.pauseRatio, 2)],
    ['Пауз / средняя', `${p.pauseCount} / ${fmt(p.meanPauseMs, 0)} мс`],
    ['F0 (тон)', `${fmt(p.pitchMeanHz, 0)} Гц`],
    ['Выразительность', `${fmt(p.pitchVariationSemitones)} пт`],
    ['Громкость / динамика', `${fmt(p.intensityMeanDb, 0)} / ${fmt(p.intensityVariationDb)} дБ`],
    ['Озвучено', fmt(p.voicedRatio, 2)],
  ];
}

function Metrics({ rows, title, tone }: { rows: Array<[string, string]>; title: string; tone: Layer }) {
  return (
    <div>
      <div className={`text-xs font-semibold mb-1 inline-block px-2 py-0.5 rounded ${LAYER_STYLE[tone]}`}>
        {title}
      </div>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-0.5 text-xs">
        {rows.map(([k, v]) => (
          <div key={k} className="flex justify-between gap-2 border-b border-slate-100 py-0.5">
            <dt className="text-slate-500">{k}</dt>
            <dd className="font-mono text-slate-800">{v}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}

/**
 * Панель «Характеристики речи → домены оценки».
 * Закрывает требования демонстрации 1 и 3: показывает, из каких лексических и
 * просодических признаков складывается система оценки и как извлечённые значения
 * соотносятся с индикаторами и доменами структурированного интервью.
 */
export function SpeechCharacteristicsPanel({ features }: Props) {
  if (!features || features.length === 0) {
    return null;
  }

  const hasAnyProsody = features.some((f) => f.prosody != null);

  return (
    <section className="bg-white rounded-lg shadow p-5 space-y-4">
      <div>
        <h3 className="text-lg font-semibold">Характеристики речи → домены оценки</h3>
        <p className="text-sm text-slate-500">
          Каждый ответ переводится в измеримые признаки двух слоёв — <span className="text-emerald-700 font-medium">лексика</span> (что
          сказано: «я/мы», слова-паразиты, термины, метрики) и{' '}
          <span className="text-indigo-700 font-medium">просодика</span> (как сказано: темп, паузы, интонация, громкость). Индикатор
          рубрикатора объявляет, на какие сигналы он опирается — это и есть мост к баллу.
        </p>
        {!hasAnyProsody && (
          <p className="mt-2 text-xs bg-amber-50 border border-amber-200 text-amber-800 rounded px-3 py-2">
            Это интервью без аудио — просодика не извлекалась (нужен аудиовход и сайдкар с Praat).
            Показаны только лексические признаки.
          </p>
        )}
      </div>

      <div className="space-y-3">
        {features.map((f) => (
          <div key={f.indicatorId} className="border border-slate-200 rounded-lg p-3 space-y-3">
            <div className="flex items-baseline justify-between gap-2 flex-wrap">
              <div>
                <span className="text-xs uppercase tracking-wide text-slate-400">{f.competencyTitle}</span>
                <div className="font-mono text-sm text-slate-800">{f.indicatorId}</div>
              </div>
              <div className="flex flex-wrap gap-1 justify-end">
                {f.signals.map((s) => {
                  const meta = SIGNAL_META[s] ?? { label: s, layer: 'lexical' as Layer };
                  return (
                    <span
                      key={s}
                      title={meta.label}
                      className={`text-[11px] px-2 py-0.5 rounded ${LAYER_STYLE[meta.layer]}`}
                    >
                      {s}
                    </span>
                  );
                })}
              </div>
            </div>

            {f.answerExcerpt && (
              <p className="text-xs text-slate-500 italic border-l-2 border-slate-200 pl-2">
                «{f.answerExcerpt}»
              </p>
            )}

            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <Metrics rows={lexicalRows(f.lexical)} title="Лексика" tone="lexical" />
              {f.prosody ? (
                <Metrics rows={prosodicRows(f.prosody)} title="Просодика" tone="prosodic" />
              ) : (
                <div className="text-xs text-slate-400 flex items-center">просодика недоступна (нет аудио)</div>
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
