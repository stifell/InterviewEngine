import type { EvaluationResult, TranscriptSegment } from '../api/types';
import { CompetencyRadar } from './CompetencyRadar';
import { IndicatorHeatmap } from './IndicatorHeatmap';
import { InterviewerPanel } from './InterviewerPanel';
import { RecommendationBadge } from './RecommendationBadge';
import { SpeechCharacteristicsPanel } from './SpeechCharacteristicsPanel';
import { TranscriptView } from './TranscriptView';

interface Props {
  result: EvaluationResult;
  segments?: TranscriptSegment[];
  onReset: () => void;
}

export function ScorecardView({ result, segments = [], onReset }: Props) {
  const { scorecard, interviewerEvaluation } = result;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between bg-white rounded-lg shadow p-4">
        <div>
          <h2 className="text-xl font-semibold">{scorecard.position}</h2>
          <p className="text-sm text-slate-500">
            Итоговый балл: <span className="font-mono">{scorecard.overallScore.toFixed(2)}</span> / 5
          </p>
        </div>
        <div className="flex items-center gap-3">
          <RecommendationBadge value={scorecard.recommendation} />
          <button
            type="button"
            className="text-sm text-slate-500 hover:text-slate-700 underline"
            onClick={onReset}
          >
            новое интервью
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <CompetencyRadar competencies={scorecard.competencies} />
        <InterviewerPanel evaluation={interviewerEvaluation} />
      </div>

      <IndicatorHeatmap competencies={scorecard.competencies} />

      <SpeechCharacteristicsPanel features={result.answerFeatures} />

      <TranscriptView segments={segments} />
    </div>
  );
}
