import { useEffect, useMemo, useRef, useState } from 'react';
import { deleteBook, getBook, listBooks, saveLog, updateBook, uploadBook } from './api';
import { RealtimeWsClient, type ConnectionStatus, type MicStatus, type RealtimeDebugEvent } from './realtime-ws';
import { fallbackBook, type Book, type BookListItem, type BookPage, type KeywordItem, type Sentence } from './story';

type DebugLog = {
  id: number;
  time: string;
  kind: RealtimeDebugEvent['kind'] | 'ui';
  message: string;
};

const connectionStatusLabels: Record<ConnectionStatus, string> = {
  idle: '未开始',
  connecting: '连接中',
  connected: '已连接',
  failed: '失败',
  closed: '已关闭',
};

const micStatusLabels: Record<MicStatus, string> = {
  idle: '未开始',
  requesting: '请求权限中',
  recording: '录音中',
  stopped: '已停止',
  failed: '失败',
};

const logKindLabels: Record<DebugLog['kind'], string> = {
  ui: '系统',
  status: '状态',
  websocket: '状态',
  event: '模型事件',
  text: 'AI 老师',
  transcript: '学生',
  audio: '音频',
  error: '错误',
};

function nowLabel(): string {
  return new Date().toLocaleTimeString();
}

function keywordText(keywords?: KeywordItem[]): string {
  return (keywords ?? []).map((item) => `${item.word}: ${item.meaning}`).join(', ');
}

function parseKeywords(value: string): KeywordItem[] {
  return value
    .split(/[,，\n]/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => {
      const [word, ...meaningParts] = item.split(/[:：]/);
      return { word: word?.trim() ?? '', meaning: meaningParts.join(':').trim() };
    })
    .filter((item) => item.word);
}

function normalizeBook(book: Book): Book {
  return {
    ...book,
    pages: book.pages.map((page, pageIndex) => ({
      ...page,
      pageNo: page.pageNo ?? pageIndex + 1,
      sentences: page.sentences.map((sentence, sentenceIndex) => ({
        ...sentence,
        index: sentenceIndex,
        chinese: sentence.chinese ?? '',
        keywords: sentence.keywords ?? [],
      })),
    })),
  };
}

function hasLearnableSentence(book: Book | null): boolean {
  return Boolean(book?.pages.some((page) => page.sentences.some((sentence) => sentence.english.trim())));
}

function App() {
  const [books, setBooks] = useState<BookListItem[]>([]);
  const [selectedBook, setSelectedBook] = useState<Book | null>(null);
  const [editableBook, setEditableBook] = useState<Book | null>(null);
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  const [currentSentenceIndex, setCurrentSentenceIndex] = useState(0);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const [micStatus, setMicStatus] = useState<MicStatus>('idle');
  const [logs, setLogs] = useState<DebugLog[]>([]);
  const [errorMessage, setErrorMessage] = useState('');
  const [noticeMessage, setNoticeMessage] = useState('');
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadTitle, setUploadTitle] = useState('');
  const [uploadEnglishTitle, setUploadEnglishTitle] = useState('');
  const [uploadLevel, setUploadLevel] = useState('初学者');
  const [isUploading, setIsUploading] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const realtimeClientRef = useRef<RealtimeWsClient | null>(null);
  const logIdRef = useRef(1);

  useEffect(() => {
    void refreshBooks();
    return () => {
      realtimeClientRef.current?.close();
    };
  }, []);

  const currentPage = selectedBook?.pages[currentPageIndex];
  const currentSentence = currentPage?.sentences[currentSentenceIndex];
  const isConnecting = connectionStatus === 'connecting';
  const isConnected = connectionStatus === 'connected';
  const canStart = connectionStatus === 'idle' || connectionStatus === 'failed' || connectionStatus === 'closed';
  const webSocketState = realtimeClientRef.current?.getWebSocketState() ?? '未创建';
  const keywords = currentSentence?.keywords ?? [];

  const progressLabel = useMemo(() => {
    if (!selectedBook || !currentPage) {
      return '请先选择绘本';
    }
    const total = Math.max(currentPage.sentences.length, 1);
    return `第 ${currentPage.pageNo} 页 · 第 ${Math.min(currentSentenceIndex + 1, total)}/${total} 句`;
  }, [selectedBook, currentPage, currentSentenceIndex]);

  const startButtonLabel = isConnecting ? '正在连接' : isConnected ? '陪练中' : '开始学习这本绘本';
  const stopButtonLabel = connectionStatus === 'closed' ? '已结束' : '结束会话';
  const atFirstSentence = currentPageIndex === 0 && currentSentenceIndex === 0;
  const atLastSentence = selectedBook
    ? currentPageIndex === selectedBook.pages.length - 1 && currentSentenceIndex === selectedBook.pages[currentPageIndex].sentences.length - 1
    : true;

  function appendLog(kind: DebugLog['kind'], message: string) {
    setLogs((currentLogs) => [
      { id: logIdRef.current++, time: nowLabel(), kind, message },
      ...currentLogs,
    ].slice(0, 100));
  }

  async function refreshBooks(nextSelectedId?: string) {
    try {
      const list = await listBooks();
      setBooks(list);
      const targetId = nextSelectedId ?? selectedBook?.id ?? list[0]?.id;
      if (targetId) {
        await handleSelectBook(targetId, false);
      } else {
        setSelectedBook(fallbackBook);
        setEditableBook(fallbackBook);
      }
    } catch (error) {
      setSelectedBook(fallbackBook);
      setEditableBook(fallbackBook);
      setErrorMessage(`绘本列表加载失败，已使用本地测试绘本：${String(error)}`);
      appendLog('error', `绘本列表加载失败：${String(error)}`);
    }
  }

  async function handleSelectBook(bookId: string, showLog = true) {
    try {
      const book = normalizeBook(await getBook(bookId));
      setSelectedBook(book);
      setEditableBook(structuredClone(book));
      setCurrentPageIndex(0);
      setCurrentSentenceIndex(0);
      setErrorMessage('');
      if (showLog) {
        appendLog('ui', `已选择绘本：${book.title}`);
      }
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    }
  }

  function handleRealtimeDebug(event: RealtimeDebugEvent) {
    appendLog(event.kind, event.message);
    if (event.kind === 'error') {
      setErrorMessage(event.message);
    }
    if (event.kind === 'transcript' && selectedBook && currentPage) {
      void saveLog({
        role: 'student',
        content: event.message,
        page: currentPage.pageNo,
        sentenceIndex: currentSentenceIndex,
        timestamp: new Date().toISOString().slice(0, 19),
      }).catch((error) => appendLog('error', `保存学生发言记录失败：${String(error)}`));
    }
  }

  async function handleUpload() {
    if (!uploadFile) {
      setErrorMessage('上传失败：文件为空，请选择 PDF、JPG 或 PNG 文件。');
      return;
    }
    setIsUploading(true);
    setErrorMessage('');
    setNoticeMessage('');
    try {
      const book = normalizeBook(await uploadBook({ file: uploadFile, title: uploadTitle, englishTitle: uploadEnglishTitle, level: uploadLevel }));
      await refreshBooks(book.id);
      setUploadFile(null);
      setUploadTitle('');
      setUploadEnglishTitle('');
      setNoticeMessage('上传成功：已解析绘本，请先校对英文句子、中文意思和重点词。');
      appendLog('ui', `上传并解析绘本成功：${book.title}`);
      const warnings = book.pages.map((page) => page.parseError).filter(Boolean).join('；');
      if (warnings) {
        setErrorMessage(warnings);
      }
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    } finally {
      setIsUploading(false);
    }
  }

  async function handleSaveBook() {
    if (!editableBook) {
      setErrorMessage('保存绘本失败：没有可保存的绘本内容。');
      return;
    }
    if (editableBook.id === 'default_story') {
      setErrorMessage('保存绘本失败：内置测试绘本不能覆盖，请先上传新绘本。');
      return;
    }
    setIsSaving(true);
    setErrorMessage('');
    try {
      const saved = normalizeBook(await updateBook(editableBook));
      setSelectedBook(saved);
      setEditableBook(structuredClone(saved));
      setNoticeMessage('保存成功：可以开始学习这本绘本。');
      appendLog('ui', `已保存绘本：${saved.title}`);
      await refreshBooks(saved.id);
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    } finally {
      setIsSaving(false);
    }
  }

  async function handleDeleteBook(bookId: string) {
    try {
      await deleteBook(bookId);
      setNoticeMessage('删除成功：已移除绘本文件和元数据。');
      appendLog('ui', `已删除绘本：${bookId}`);
      await refreshBooks();
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    }
  }

  function updateEditable(mutator: (book: Book) => Book) {
    setEditableBook((book) => (book ? mutator(structuredClone(book)) : book));
  }

  function updatePage(pageIndex: number, patch: Partial<BookPage>) {
    updateEditable((book) => {
      book.pages[pageIndex] = { ...book.pages[pageIndex], ...patch };
      return book;
    });
  }

  function updateSentence(pageIndex: number, sentenceIndex: number, patch: Partial<Sentence>) {
    updateEditable((book) => {
      book.pages[pageIndex].sentences[sentenceIndex] = { ...book.pages[pageIndex].sentences[sentenceIndex], ...patch };
      return normalizeBook(book);
    });
  }

  function addSentence(pageIndex: number) {
    updateEditable((book) => {
      book.pages[pageIndex].sentences.push({ index: book.pages[pageIndex].sentences.length, english: '', chinese: '', keywords: [] });
      return normalizeBook(book);
    });
  }

  function removeSentence(pageIndex: number, sentenceIndex: number) {
    updateEditable((book) => {
      book.pages[pageIndex].sentences.splice(sentenceIndex, 1);
      return normalizeBook(book);
    });
  }

  function applyEditedBookToLearning() {
    if (!editableBook) {
      return;
    }
    const normalized = normalizeBook(editableBook);
    setSelectedBook(normalized);
    setCurrentPageIndex(0);
    setCurrentSentenceIndex(0);
    appendLog('ui', `已将编辑区内容用于学习：${normalized.title}`);
  }

  async function handleStart() {
    if (!selectedBook) {
      setErrorMessage('用户未选择绘本就点击开始陪练，请先选择或上传绘本。');
      return;
    }
    if (!currentSentence?.english.trim() || !hasLearnableSentence(selectedBook)) {
      setErrorMessage('当前绘本还没有可学习的英文句子，请先编辑并保存。');
      return;
    }
    setErrorMessage('');
    setConnectionStatus('connecting');
    setMicStatus('idle');
    realtimeClientRef.current?.close();
    const client = new RealtimeWsClient();
    realtimeClientRef.current = client;

    try {
      await client.connect({
        book: selectedBook,
        pageNo: currentPage?.pageNo ?? 1,
        currentSentence,
        onConnectionStatusChange: setConnectionStatus,
        onMicStatusChange: setMicStatus,
        onDebugEvent: handleRealtimeDebug,
      });
      await client.startMic();
      appendLog('ui', 'AI 英语阅读陪练已开始。');
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      setErrorMessage(message);
      setConnectionStatus('failed');
      appendLog('error', message);
      client.close();
    }
  }

  function handleStop() {
    realtimeClientRef.current?.close();
    appendLog('ui', '已结束本次陪练会话。');
  }

  function handleRepeat() {
    if (!currentSentence?.english.trim()) {
      setErrorMessage('当前绘本还没有可学习的英文句子，请先编辑并保存。');
      return;
    }
    try {
      realtimeClientRef.current?.repeatSentence(currentSentence);
      appendLog('ui', `已请求 AI 老师重复朗读：${currentSentence.english}`);
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    }
  }

  function moveSentence(direction: 1 | -1) {
    if (!selectedBook || !currentPage) {
      return;
    }
    let nextPageIndex = currentPageIndex;
    let nextSentenceIndex = currentSentenceIndex + direction;

    if (direction > 0 && nextSentenceIndex >= currentPage.sentences.length) {
      nextPageIndex = Math.min(currentPageIndex + 1, selectedBook.pages.length - 1);
      nextSentenceIndex = nextPageIndex === currentPageIndex ? currentSentenceIndex : 0;
    }
    if (direction < 0 && nextSentenceIndex < 0) {
      nextPageIndex = Math.max(currentPageIndex - 1, 0);
      nextSentenceIndex = nextPageIndex === currentPageIndex ? currentSentenceIndex : selectedBook.pages[nextPageIndex].sentences.length - 1;
    }

    setCurrentPageIndex(nextPageIndex);
    setCurrentSentenceIndex(nextSentenceIndex);
    const nextSentence = selectedBook.pages[nextPageIndex].sentences[nextSentenceIndex];
    appendLog('ui', `已切换到新句子：${nextSentence.english}`);

    if (isConnected) {
      try {
        realtimeClientRef.current?.updateSentence(selectedBook, selectedBook.pages[nextPageIndex].pageNo, nextSentence);
      } catch (error) {
        setErrorMessage(String(error));
        appendLog('error', String(error));
      }
    }
  }

  return (
    <main className="app-shell">
      <section className="hero-card">
        <div>
          <p className="eyebrow">绘本上传 + 中文讲解 + 英文朗读 + 实时语音互动</p>
          <h1>H5 中文讲解式 AI 英语阅读导师 Demo</h1>
          <p className="subtitle">上传 PDF、JPG 或 PNG 绘本，校对句子后让 AI 老师围绕当前绘本逐句带读和反馈。</p>
        </div>
        <div className={`status-pill status-${connectionStatus}`}>{connectionStatusLabels[connectionStatus]}</div>
      </section>

      <section className="manager-grid">
        <article className="card manager-card">
          <p className="section-label">绘本管理区</p>
          <h2>上传绘本</h2>
          <p className="help-text">支持 PDF、JPG、PNG。普通 PDF 可自动提取文本，图片和扫描版 PDF 需要 OCR 或手动校对。</p>
          <div className="upload-grid">
            <label>选择文件<input type="file" accept=".pdf,.jpg,.jpeg,.png" onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)} /></label>
            <label>中文名<input value={uploadTitle} onChange={(event) => setUploadTitle(event.target.value)} placeholder="例如：小兔子" /></label>
            <label>英文名<input value={uploadEnglishTitle} onChange={(event) => setUploadEnglishTitle(event.target.value)} placeholder="例如：The Little Rabbit" /></label>
            <label>等级<input value={uploadLevel} onChange={(event) => setUploadLevel(event.target.value)} placeholder="初学者 / 进阶" /></label>
          </div>
          <button type="button" className="primary" onClick={handleUpload} disabled={isUploading}>{isUploading ? '上传解析中……' : '上传并解析'}</button>
        </article>

        <article className="card manager-card">
          <p className="section-label">已上传绘本</p>
          <h2>选择绘本</h2>
          <div className="book-list">
            {books.map((book) => (
              <div key={book.id} className={selectedBook?.id === book.id ? 'book-item active' : 'book-item'}>
                <button type="button" onClick={() => void handleSelectBook(book.id)}>
                  <strong>{book.title}</strong>
                  <span>{book.englishTitle} · {book.level} · {book.pageCount} 页</span>
                </button>
                {book.id !== 'default_story' && <button type="button" className="danger small" onClick={() => void handleDeleteBook(book.id)}>删除</button>}
              </div>
            ))}
          </div>
          <p className="help-text">当前选中：{selectedBook ? `${selectedBook.title} / ${selectedBook.englishTitle}` : '未选择'}</p>
        </article>
      </section>

      <section className="content-grid wide">
        <article className="story-panel card editor-panel">
          <div className="story-header">
            <div>
              <p className="section-label">绘本编辑区</p>
              <h2>编辑绘本内容</h2>
            </div>
            <div className="button-row compact">
              <button type="button" onClick={applyEditedBookToLearning} disabled={!editableBook}>开始学习这本绘本</button>
              <button type="button" className="primary" onClick={() => void handleSaveBook()} disabled={!editableBook || isSaving || editableBook.id === 'default_story'}>{isSaving ? '保存中……' : '保存绘本'}</button>
            </div>
          </div>

          {editableBook && (
            <div className="editor-form">
              <div className="upload-grid">
                <label>绘本中文名<input value={editableBook.title} onChange={(event) => updateEditable((book) => ({ ...book, title: event.target.value }))} /></label>
                <label>绘本英文名<input value={editableBook.englishTitle} onChange={(event) => updateEditable((book) => ({ ...book, englishTitle: event.target.value }))} /></label>
                <label>等级<input value={editableBook.level} onChange={(event) => updateEditable((book) => ({ ...book, level: event.target.value }))} /></label>
              </div>
              {editableBook.pages.map((page, pageIndex) => (
                <div key={page.pageNo} className="edit-page">
                  <div className="edit-page-header">
                    <h3>第 {page.pageNo} 页</h3>
                    {page.needOcr && <span className="warning-pill">需要 OCR / 手动校对</span>}
                  </div>
                  {page.parseError && <div className="warning-box">{page.parseError}</div>}
                  <label>页面原始文本<textarea value={page.rawText ?? ''} onChange={(event) => updatePage(pageIndex, { rawText: event.target.value })} /></label>
                  {page.sentences.map((sentence, sentenceIndex) => (
                    <div key={`${page.pageNo}-${sentenceIndex}`} className="sentence-editor">
                      <label>英文原句：<input value={sentence.english} onChange={(event) => updateSentence(pageIndex, sentenceIndex, { english: event.target.value })} /></label>
                      <label>中文意思：<input value={sentence.chinese} onChange={(event) => updateSentence(pageIndex, sentenceIndex, { chinese: event.target.value })} /></label>
                      <label>重点词：<input value={keywordText(sentence.keywords)} onChange={(event) => updateSentence(pageIndex, sentenceIndex, { keywords: parseKeywords(event.target.value) })} placeholder="例如 rabbit: 小兔子, red hat: 红帽子" /></label>
                      <button type="button" className="danger small" onClick={() => removeSentence(pageIndex, sentenceIndex)}>删除错误句子</button>
                    </div>
                  ))}
                  <button type="button" onClick={() => addSentence(pageIndex)}>新增句子</button>
                </div>
              ))}
            </div>
          )}
        </article>

        <aside className="debug-panel card">
          <p className="section-label">对话记录</p>
          <h2>对话活动</h2>
          <dl className="debug-stats">
            <div><dt>连接状态</dt><dd>{connectionStatusLabels[connectionStatus]}</dd></div>
            <div><dt>数据通道</dt><dd>{webSocketState}</dd></div>
            <div><dt>麦克风</dt><dd>{micStatusLabels[micStatus]}</dd></div>
          </dl>
          <div className="log-list" aria-live="polite">
            {logs.length === 0 ? <p className="empty-log">还没有对话活动。点击“开始学习这本绘本”，允许麦克风权限后开始学习。</p> : logs.map((log) => (
              <div key={log.id} className={`log-item log-${log.kind}`}><span>{log.time}</span><strong>{logKindLabels[log.kind]}</strong><p>{log.message}</p></div>
            ))}
          </div>
        </aside>
      </section>

      <section className="content-grid">
        <article className="story-panel card">
          <div className="story-header">
            <div>
              <p className="section-label">阅读学习区</p>
              <h2>{selectedBook ? `故事：${selectedBook.title}` : '请先选择绘本'}</h2>
              {selectedBook?.englishTitle && <p className="english-title">英文名：{selectedBook.englishTitle}</p>}
            </div>
            <span className="level-badge">等级：{selectedBook?.level ?? '……'}</span>
          </div>
          <div className="progress-row"><span>当前进度：{progressLabel}</span><span>麦克风：{micStatusLabels[micStatus]}</span></div>
          <div className="page-card">
            {currentPage?.sentences.length ? currentPage.sentences.map((sentence, index) => (
              <div key={`${currentPage.pageNo}-${index}`} className={index === currentSentenceIndex ? 'sentence active' : 'sentence'}>
                <span className="sentence-number">{index + 1}</span>
                <div><p className="sentence-english">{sentence.english || '（空句子，请先编辑）'}</p><p className="sentence-chinese">{sentence.chinese || '（中文意思待填写）'}</p></div>
              </div>
            )) : <p className="empty-log">当前绘本还没有可学习的英文句子，请先编辑并保存。</p>}
          </div>
          <div className="current-sentence-box">
            <span>当前句子</span>
            <p className="current-label">英文原句：</p><strong>{currentSentence?.english || '请先编辑英文句子'}</strong>
            <p className="current-label">中文意思：</p><p className="current-chinese">{currentSentence?.chinese || '请填写中文意思，方便 AI 用中文讲解。'}</p>
            <div className="keyword-list"><p className="current-label">重点词：</p>{keywords.length ? keywords.map((keyword) => <span key={`${keyword.word}-${keyword.meaning}`}>{keyword.word}：{keyword.meaning}</span>) : <em>暂无重点词，可在编辑区补充。</em>}</div>
          </div>
          <div className="button-row">
            <button type="button" onClick={() => moveSentence(-1)} disabled={!isConnected || atFirstSentence}>上一句</button>
            <button type="button" onClick={() => moveSentence(1)} disabled={!isConnected || atLastSentence}>下一句</button>
            <button type="button" className="primary" onClick={() => void handleStart()} disabled={!canStart || isConnecting || !selectedBook}>{startButtonLabel}</button>
            <button type="button" onClick={handleRepeat} disabled={!isConnected}>重复朗读</button>
            <button type="button" className="danger" onClick={handleStop} disabled={!isConnected && !isConnecting}>{stopButtonLabel}</button>
          </div>
          {noticeMessage && <div className="notice-box">{noticeMessage}</div>}
          {errorMessage && <div className="error-box">错误：{errorMessage}</div>}
        </article>
      </section>
    </main>
  );
}

export default App;
