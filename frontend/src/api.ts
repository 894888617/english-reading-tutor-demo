import type { StoryResponse } from './story';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

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
  const response = await fetch(`${API_BASE_URL}/api/story`);
  if (!response.ok) {
    throw new Error(`故事加载失败： ${await parseError(response)}`);
  }
  return response.json();
}

export async function saveLog(log: TutorLog): Promise<{ success: boolean }> {
  const response = await fetch(`${API_BASE_URL}/api/logs`, {
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
