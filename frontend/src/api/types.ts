// Типы, синхронные с доменом бэкенда (см. backend/src/main/java/com/interviewengine/domain).
// Спецификация — это то, что записывает Jackson, поэтому имена полей строго совпадают.

export type SpeakerRole = 'INTERVIEWER' | 'CANDIDATE';

export interface TranscriptSegment {
  speaker: SpeakerRole;
  blockId: string;
  text: string;
  startMs?: number | null;
  endMs?: number | null;
}

export interface IndicatorEvaluation {
  indicatorId: string;
  score: number;
  acceptable: boolean;
  evidenceQuote: string;
  rationale: string;
}

export interface CompetencyEvaluation {
  competencyId: string;
  title: string;
  weight: number;
  score: number;
  acceptable: boolean;
  indicators: IndicatorEvaluation[];
}

export type Recommendation = 'STRONG_HIRE' | 'HIRE' | 'NO_HIRE';

export interface Scorecard {
  position: string;
  overallScore: number;
  recommendation: Recommendation;
  competencies: CompetencyEvaluation[];
}

export type NeutralityFlagKind = 'SUGGESTIVE_AGREEMENT' | 'ANSWER_REFRAMING' | 'ANSWER_HINT';

export interface NeutralityFlag {
  kind: NeutralityFlagKind;
  blockId: string;
  trigger: string;
  quote: string;
}

export interface TimingDeviation {
  blockId: string;
  plannedMinutes: number;
  actualMinutes: number;
  deviationMinutes: number;
}

export interface InterviewerEvaluation {
  mainQuestionCoverage: number;
  probeCoverage: number;
  scriptAdherence: number;
  missedProbeIds: string[];
  neutralityFlags: NeutralityFlag[];
  timingDeviations: TimingDeviation[];
  notes: string[];
}

/** Лексические (комп-лингвистические) признаки ответа — R4, считаются в Java. */
export interface LinguisticFeatures {
  answerLength: number;
  firstPersonSingularCount: number;
  firstPersonPluralCount: number;
  hasFirstPerson: boolean;
  ownershipRatio: number;
  fillerDensity: number;
  hedgingDensity: number;
  hasMetrics: boolean;
  lexicalDiversity: number;
}

/** Просодические признаки речевого сигнала — извлекаются в сайдкаре через Praat. */
export interface ProsodicFeatures {
  speechRateWpm: number;
  articulationRateWpm: number;
  pauseRatio: number;
  pauseCount: number;
  meanPauseMs: number;
  pitchMeanHz: number;
  pitchVariationSemitones: number;
  intensityMeanDb: number;
  intensityVariationDb: number;
  voicedRatio: number;
  durationMs: number;
}

/** Снимок признаков ответа с привязкой к индикатору/домену — для демонстрации. */
export interface AnswerFeatureView {
  indicatorId: string;
  competencyId: string;
  competencyTitle: string;
  answerExcerpt: string;
  lexical: LinguisticFeatures;
  prosody: ProsodicFeatures | null;
  signals: string[];
}

export interface EvaluationResult {
  position: string;
  scorecard: Scorecard;
  interviewerEvaluation: InterviewerEvaluation;
  answerFeatures: AnswerFeatureView[];
}

export type InterviewStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED';

export interface InterviewView {
  id: string;
  position: string;
  status: InterviewStatus;
  segments: TranscriptSegment[];
  errorMessage: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EvaluationStartedResponse {
  taskId: string;
  interviewId: string;
  status: InterviewStatus;
}

export interface ModelInfo {
  id: string;
  displayName: string;
  /** Максимальный контекст в токенах — возвращается Gemini API напрямую. */
  inputTokenLimit: number;
}

export interface SettingsView {
  currentModel: string;
  availableModels: ModelInfo[];
}

// Подмножество рубрикатора, нужное фронту, чтобы рисовать названия индикаторов и BARS.
export interface Indicator {
  id: string;
  text: string;
  signals: string[];
  bars: Record<string, string>;
  acceptableFrom: number;
}

export interface Competency {
  id: string;
  title: string;
  block: string;
  weight: number;
  indicators: Indicator[];
}

export interface InterviewBlock {
  id: string;
  order: number;
  title: string;
  timingMinutes: number;
  mainQuestion: string;
  probes: Array<{ id: string; text: string; indicator: string }>;
}

export interface Rubric {
  position: string;
  durationMinutes: number;
  blocks: InterviewBlock[];
  competencies: Competency[];
}
