import type { ReadingAssessmentResult } from './analysis/pronunciationDiff';
import type { Book, BookListItem, StoryResponse } from './story';

const BACKEND_BASE_URL = import.meta.env.VITE_API_BASE_URL || '';
const API_BASE_URL = `${BACKEND_BASE_URL}/api`;

export function resolveAssetUrl(url: string) {
  if (!url) return '';

  if (url.startsWith('http://') || url.startsWith('https://')) {
    return url;
  }

  if (url.startsWith('/')) {
    return `${BACKEND_BASE_URL}${url}`;
  }

  return `${BACKEND_BASE_URL}/${url}`;
}

export interface TutorLog {
  role: string;
  content: string;
  page: number;
  sentenceIndex: number;
  timestamp: string;
}

async function parseError(response: Response): Promise<string> {
  const text = await response.text();
  if (!text) {
    return `${response.status} ${response.statusText}`;
  }
  try {
    const json = JSON.parse(text);
    return json.message || json.error || text;
  } catch {
    return text;
  }
}

export async function getStory(): Promise<StoryResponse> {
  const response = await fetch(`${API_BASE_URL}/story`);
  if (!response.ok) {
    throw new Error(`故事加载失败： ${await parseError(response)}`);
  }
  return response.json();
}

export async function listBooks(): Promise<BookListItem[]> {
  const response = await fetch(`${API_BASE_URL}/books`);
  if (!response.ok) {
    throw new Error(`绘本列表加载失败：${await parseError(response)}`);
  }
  return response.json();
}

export async function getBook(bookId: string): Promise<Book> {
  const response = await fetch(`${API_BASE_URL}/books/${encodeURIComponent(bookId)}`);
  if (!response.ok) {
    throw new Error(`读取绘本失败：${await parseError(response)}`);
  }
  return response.json();
}

export async function uploadBook(input: { file: File; title?: string; englishTitle?: string; level?: string }): Promise<Book> {
  const form = new FormData();
  form.append('file', input.file);
  form.append('title', input.title ?? '');
  form.append('englishTitle', input.englishTitle ?? '');
  form.append('level', input.level ?? '');
  const response = await fetch(`${API_BASE_URL}/books/upload`, {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json();
}

export async function updateBook(book: Book): Promise<Book> {
  const response = await fetch(`${API_BASE_URL}/books/${encodeURIComponent(book.id)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(book),
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json();
}

export async function deleteBook(bookId: string): Promise<{ success: boolean }> {
  const response = await fetch(`${API_BASE_URL}/books/${encodeURIComponent(bookId)}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json();
}

export async function saveLog(log: TutorLog): Promise<{ success: boolean }> {
  const response = await fetch(`${API_BASE_URL}/logs`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(log),
  });

  if (!response.ok) {
    throw new Error(`保存对话记录失败： ${await parseError(response)}`);
  }
  return response.json();
}

export async function assessReading(input: {
  audio: Blob;
  targetText: string;
  bookId?: string;
  pageNo?: number;
  sentenceIndex?: number;
  recognizedText?: string;
  mimeType?: string;
}): Promise<ReadingAssessmentResult> {
  const referenceText = input.targetText.trim();
  if (!referenceText) {
    throw new Error('referenceText 不能为空，请先选择当前句子的英文原文。');
  }
  if (input.audio.size === 0) {
    throw new Error('录音文件为空，请重新录音');
  }

  const form = new FormData();
  const actualMimeType = input.mimeType || input.audio.type || 'audio/webm';
  const extension = audioExtension(actualMimeType);
  // 必须上传真实录音 Blob 二进制；不要只传 audioUrl 或浏览器本地 blob URL。
  form.append('file', input.audio, `recording.${extension}`);
  form.append('referenceText', referenceText);
  form.append('bookId', input.bookId ?? '');
  form.append('pageId', String(input.pageNo ?? 1));
  form.append('sentenceId', String(input.sentenceIndex ?? 0));
  form.append('mimeType', actualMimeType);
  if (input.recognizedText) {
    form.append('recognizedText', input.recognizedText);
  }
  const response = await fetch(`${API_BASE_URL}/speech/evaluate`, { method: 'POST', body: form });
  const text = await response.text();
  console.info('[speech/evaluate] status=%s response=%s', response.status, text);
  if (!text) {
    throw new Error('评分接口返回空响应');
  }

  let payload: unknown;
  try {
    payload = JSON.parse(text);
  } catch (error) {
    throw new Error(`评分接口返回格式异常：${String(error)}`);
  }

  if (!response.ok) {
    throw new Error(apiMessage(payload) || `${response.status} ${response.statusText}`);
  }

  if (isApiEnvelope(payload)) {
    if (!payload.success) {
      throw new Error(apiMessage(payload) || '评分失败，请重新录音');
    }
    return normalizeAssessmentResult(payload.data);
  }

  return normalizeAssessmentResult(payload);
}

function isApiEnvelope(payload: unknown): payload is { success: boolean; data?: unknown; message?: string } {
  return typeof payload === 'object' && payload !== null && 'success' in payload;
}

function apiMessage(payload: unknown): string {
  if (typeof payload !== 'object' || payload === null) return '';
  const record = payload as Record<string, unknown>;
  return typeof record.message === 'string' ? record.message : typeof record.error === 'string' ? record.error : '';
}

function normalizeAssessmentResult(payload: unknown): ReadingAssessmentResult {
  if (typeof payload !== 'object' || payload === null) {
    throw new Error('评分接口返回格式异常');
  }
  const data = payload as Record<string, unknown>;
  if (typeof data.score === 'object' && data.score !== null && Array.isArray(data.wordResults)) {
    return data as ReadingAssessmentResult;
  }
  const score = {
    totalScore: numberValue(data.totalScore),
    accuracyScore: numberValue(data.accuracyScore),
    fluencyScore: numberValue(data.fluencyScore),
    completenessScore: numberValue(data.completenessScore),
    clarityScore: numberValue(data.clarityScore),
  };
  return {
    evaluationId: stringValue(data.evaluationId),
    targetText: stringValue(data.targetText),
    recognizedText: stringValue(data.recognizedText),
    score,
    wordResults: Array.isArray(data.wordResults)
      ? data.wordResults as ReadingAssessmentResult['wordResults']
      : Array.isArray(data.words)
        ? data.words as ReadingAssessmentResult['wordResults']
        : [],
    issues: Array.isArray(data.issues) ? data.issues as ReadingAssessmentResult['issues'] : [],
    feedbackText: stringValue(data.feedbackText),
    feedbackAudioUrl: stringValue(data.feedbackAudioUrl),
    recordingUrl: stringValue(data.recordingUrl),
    pcmGenerated: typeof data.pcmGenerated === 'boolean' ? data.pcmGenerated : undefined,
  };
}

function numberValue(value: unknown): number {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0;
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : '';
}

export type TtsVoice = {
  id: string;
  name: string;
  model: string;
  language: string;
  gender: string;
  description: string;
};

export type TtsResult = {
  audioUrl: string;
  cacheHit: boolean;
  durationMs?: number;
  provider: string;
  model: string;
  voice: string;
  language: string;
  speed: number;
  pitch: number;
  volume: number;
  format: string;
};

export async function getTtsVoices(): Promise<TtsVoice[]> {
  const response = await fetch(`${API_BASE_URL}/tts/voices`);
  if (!response.ok) throw new Error(`声音列表加载失败：${await parseError(response)}`);
  return response.json();
}

function audioExtension(mimeType: string) {
  const normalized = mimeType.toLowerCase();
  if (normalized.includes('wav')) return 'wav';
  if (normalized.includes('mp4')) return 'mp4';
  if (normalized.includes('mpeg') || normalized.includes('mp3')) return 'mp3';
  if (normalized.includes('ogg')) return 'ogg';
  return 'webm';
}

export async function synthesizeTts(input: {
  text: string;
  language: 'English' | 'Chinese' | 'en' | 'zh';
  voice: string;
  speed?: number;
  pitch?: number;
  volume?: number;
  format?: 'mp3' | 'wav' | 'pcm';
  bookId?: string;
  pageId?: string;
  sentenceId?: string;
  forceRefresh?: boolean;
}): Promise<TtsResult> {
  const response = await fetch(`${API_BASE_URL}/tts/synthesize`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(await parseError(response));
  return response.json();
}

export async function clearTtsCache(): Promise<{ success: boolean; deleted: number }> {
  const response = await fetch(`${API_BASE_URL}/tts/cache/clear`, { method: 'POST' });
  if (!response.ok) throw new Error(await parseError(response));
  return response.json();
}

export async function createSpeechFeedback(input: { evaluationId: string; voice: string }): Promise<{ text: string; audioUrl: string }> {
  const response = await fetch(`${API_BASE_URL}/feedback/speech`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (!response.ok) throw new Error(await parseError(response));
  return response.json();
}
