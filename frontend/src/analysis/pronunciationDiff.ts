export type ReadMode = 'page' | 'sentence' | 'repeat' | 'slow' | 'word';
export type RecordingStatus = 'idle' | 'recording' | 'uploading' | 'scoring' | 'done' | 'failed';
export type VoiceStyle = 'professional_female' | 'professional_male' | 'child_friendly_female' | 'child_friendly_male';

export type WordToken = {
  index: number;
  text: string;
  normalized: string;
  meaning?: string;
  status?: 'correct' | 'wrong' | 'missed' | 'extra' | 'unclear' | 'current';
};

export type ReadingScore = {
  totalScore: number;
  accuracyScore: number;
  fluencyScore: number;
  completenessScore: number;
  clarityScore: number;
};

export type PronunciationIssue = {
  type: 'wrong' | 'missed' | 'extra' | 'unclear';
  targetWord?: string;
  actualWord?: string;
  wordIndex?: number;
  message: string;
  suggestion: string;
};

export type ReadingAssessmentResult = {
  targetText: string;
  recognizedText: string;
  score: ReadingScore;
  wordResults: WordToken[];
  issues: PronunciationIssue[];
  feedbackText: string;
};

export function tokenizeSentence(text: string): WordToken[] {
  const matches = text.match(/[A-Za-z]+(?:'[A-Za-z]+)?|\d+/g) ?? [];
  return matches.map((word, index) => ({ index, text: word, normalized: normalizeWord(word), status: 'correct' }));
}

export function wordMeaning(word: string, keywords: { word: string; meaning: string }[] = []): string | undefined {
  const normalized = normalizeWord(word);
  return keywords.find((item) => normalizeWord(item.word) === normalized)?.meaning;
}

export function normalizeWord(word: string): string {
  return word.toLowerCase().replace(/[^a-z0-9']/g, '');
}
