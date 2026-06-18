import {
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
} from 'recharts';
import type { CompetencyEvaluation } from '../api/types';

interface Props {
  competencies: CompetencyEvaluation[];
}

/**
 * Радар по компетенциям — закрывает требование R2 «точная конкретная шкала + визуализация»
 * (§1 CLAUDE.md). Шкала фиксированно 0..5, чтобы радары разных кандидатов были сравнимы.
 */
export function CompetencyRadar({ competencies }: Props) {
  const data = competencies.map((c) => ({
    competency: c.title,
    score: Number(c.score.toFixed(2)),
    weight: c.weight,
  }));

  return (
    <div className="bg-white rounded-lg shadow p-4">
      <h3 className="text-lg font-semibold mb-2">Радар по компетенциям</h3>
      <div className="w-full h-80">
        <ResponsiveContainer width="100%" height="100%">
          <RadarChart data={data} outerRadius="75%">
            <PolarGrid />
            <PolarAngleAxis dataKey="competency" />
            <PolarRadiusAxis domain={[0, 5]} tickCount={6} />
            <Tooltip
              formatter={(value: number, _name, item) => {
                const weight = (item.payload as { weight: number }).weight;
                return [`${value} / 5 (вес ${weight.toFixed(2)})`, 'балл'];
              }}
            />
            <Radar
              name="балл"
              dataKey="score"
              stroke="#2563eb"
              fill="#3b82f6"
              fillOpacity={0.45}
            />
          </RadarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
