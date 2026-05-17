import type { Book, BookListItem, StoryResponse } from './story';

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

export async function listBooks(): Promise<BookListItem[]> {
  const response = await fetch(`${API_BASE_URL}/api/books`);
  if (!response.ok) {
    throw new Error(`绘本列表加载失败：${await parseError(response)}`);
  }
  return response.json();
}

export async function getBook(bookId: string): Promise<Book> {
  const response = await fetch(`${API_BASE_URL}/api/books/${encodeURIComponent(bookId)}`);
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
  const response = await fetch(`${API_BASE_URL}/api/books/upload`, {
    method: 'POST',
    body: form,
  });
  if (!response.ok) {
    throw new Error(await parseError(response));
  }
  return response.json();
}

export async function updateBook(book: Book): Promise<Book> {
  const response = await fetch(`${API_BASE_URL}/api/books/${encodeURIComponent(book.id)}`, {
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
  const response = await fetch(`${API_BASE_URL}/api/books/${encodeURIComponent(bookId)}`, { method: 'DELETE' });
  if (!response.ok) {
    throw new Error(await parseError(response));
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
