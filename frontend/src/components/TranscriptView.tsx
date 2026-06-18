import { useState } from 'react';
import type { TranscriptSegment } from '../api/types';

interface Props {
  segments: TranscriptSegment[];
  /** blockId → человекочитаемый заголовок блока */
  blockTitles?: Record<string, string>;
}

function formatMs(ms?: number | null): string {
  if (ms == null) return '';
  const totalSec = Math.floor(ms / 1000);
  const min = Math.floor(totalSec / 60);
  const sec = totalSec % 60;
  return `${min}:${sec.toString().padStart(2, '0')}`;
}

function defaultBlockLabel(blockId: string): string {
  const m = blockId.match(/^block(\d+)$/);
  return m ? `Блок ${m[1]}` : blockId;
}

function groupByBlock(segments: TranscriptSegment[]) {
  const result: { blockId: string; segs: TranscriptSegment[] }[] = [];
  for (const seg of segments) {
    const last = result[result.length - 1];
    if (last && last.blockId === seg.blockId) {
      last.segs.push(seg);
    } else {
      result.push({ blockId: seg.blockId, segs: [seg] });
    }
  }
  return result;
}

export function TranscriptView({ segments, blockTitles = {} }: Props) {
  const [open, setOpen] = useState(false);

  if (segments.length === 0) return null;

  const blocks = groupByBlock(segments);
  const interviewerCount = segments.filter((s) => s.speaker === 'INTERVIEWER').length;
  // Моно-аудио: нет ни одного интервьюера → показываем без ролей
  const isMono = interviewerCount === 0;

  return (
    <div className="bg-white rounded-lg shadow">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="w-full flex items-center justify-between px-6 py-4 text-left hover:bg-slate-50 rounded-lg transition-colors"
      >
        <div>
          <span className="font-semibold text-slate-700">Транскрипт интервью</span>
          <span className="ml-3 text-xs text-slate-400 font-mono">
            {segments.length} реплик · {blocks.length} блок{blocks.length === 1 ? '' : 'а/ов'}
            {!isMono && ` · И:${interviewerCount} К:${segments.length - interviewerCount}`}
          </span>
          {isMono && (
            <span className="ml-2 text-xs text-amber-600 bg-amber-50 px-2 py-0.5 rounded-full">
              моно · роли не определены
            </span>
          )}
        </div>
        <span className="text-slate-400 text-sm select-none">
          {open ? '▲ скрыть' : '▼ развернуть'}
        </span>
      </button>

      {open && (
        <div className="border-t divide-y divide-slate-100">
          {isMono && (
            <div className="px-6 py-3 bg-amber-50 text-amber-800 text-xs">
              Аудио записано в одном канале — разделить голоса интервьюера и кандидата
              автоматически невозможно. Для разделения используйте стерео-запись (каждый
              участник на своём канале) или включите диаризацию через pyannote.
            </div>
          )}
          {blocks.map(({ blockId, segs }) => (
            <BlockSection
              key={blockId}
              blockId={blockId}
              title={blockTitles[blockId] ?? defaultBlockLabel(blockId)}
              segments={segs}
              isMono={isMono}
            />
          ))}
        </div>
      )}
    </div>
  );
}

// ── Один блок ─────────────────────────────────────────────────────────────────

interface BlockSectionProps {
  blockId: string;
  title: string;
  segments: TranscriptSegment[];
  isMono: boolean;
}

function BlockSection({ title, segments, isMono }: BlockSectionProps) {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <div className="px-6 py-4">
      <button
        type="button"
        onClick={() => setCollapsed((v) => !v)}
        className="flex items-center gap-2 mb-3 group"
      >
        <span className="text-xs font-semibold uppercase tracking-wide text-slate-500 group-hover:text-slate-700">
          {title}
        </span>
        <span className="text-xs text-slate-300 group-hover:text-slate-500">
          ({segments.length}) {collapsed ? '▼' : '▲'}
        </span>
      </button>

      {!collapsed && (
        <div className="space-y-2 max-h-[500px] overflow-y-auto pr-1">
          {segments.map((seg, i) =>
            isMono
              ? <MonoSegmentLine key={i} seg={seg} />
              : <SegmentBubble key={i} seg={seg} />
          )}
        </div>
      )}
    </div>
  );
}

// ── Реплика в режиме с ролями (пузырь) ────────────────────────────────────────

function SegmentBubble({ seg }: { seg: TranscriptSegment }) {
  const isInterviewer = seg.speaker === 'INTERVIEWER';
  const ts = formatMs(seg.startMs);

  return (
    <div className={`flex gap-2 ${isInterviewer ? '' : 'flex-row-reverse'}`}>
      <div
        className={`flex-shrink-0 w-7 h-7 rounded-full flex items-center justify-center text-xs font-bold text-white ${
          isInterviewer ? 'bg-blue-500' : 'bg-emerald-500'
        }`}
      >
        {isInterviewer ? 'И' : 'К'}
      </div>
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-2 text-sm leading-relaxed ${
          isInterviewer
            ? 'bg-blue-50 text-blue-900 rounded-tl-sm'
            : 'bg-emerald-50 text-emerald-900 rounded-tr-sm'
        }`}
      >
        {ts && <span className="text-xs font-mono opacity-40 mr-2">{ts}</span>}
        {seg.text}
      </div>
    </div>
  );
}

// ── Реплика в моно-режиме (строка без роли) ───────────────────────────────────

function MonoSegmentLine({ seg }: { seg: TranscriptSegment }) {
  const ts = formatMs(seg.startMs);
  return (
    <div className="flex gap-3 text-sm text-slate-700 leading-relaxed py-0.5">
      {ts && (
        <span className="flex-shrink-0 text-xs font-mono text-slate-400 w-10 pt-0.5">
          {ts}
        </span>
      )}
      <span>{seg.text}</span>
    </div>
  );
}
