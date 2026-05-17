import { useEffect, useMemo, useRef, useState } from 'react';
import { assessReading, deleteBook, getBook, listBooks, saveLog, updateBook, uploadBook } from './api';
import { RealtimeWsClient, type ConnectionStatus, type MicStatus, type RealtimeDebugEvent } from './realtime-ws';
import { tokenizeSentence, wordMeaning, type ReadingAssessmentResult, type ReadMode, type RecordingStatus, type VoiceStyle, type WordToken } from './analysis/pronunciationDiff';
import { Recorder, type RecorderResult } from './recording/Recorder';
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
  system: '系统',
  reading: '朗读',
  recording: '录音',
  assessment: '评分',
  correction: '纠错',
  status: '系统',
  websocket: '系统',
  event: '系统',
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
  const [voiceStyle, setVoiceStyle] = useState<VoiceStyle>('professional_female');
  const [isAiPlaying, setIsAiPlaying] = useState(false);
  const [readMode, setReadMode] = useState<ReadMode | null>(null);
  const [selectedWord, setSelectedWord] = useState<WordToken | null>(null);
  const [recordingStatus, setRecordingStatus] = useState<RecordingStatus>('idle');
  const [recordingResult, setRecordingResult] = useState<RecorderResult | null>(null);
  const [assessmentResult, setAssessmentResult] = useState<ReadingAssessmentResult | null>(null);
  const recorderRef = useRef<Recorder | null>(null);
  const readTimerRef = useRef<number | null>(null);
  const realtimeClientRef = useRef<RealtimeWsClient | null>(null);
  const logIdRef = useRef(1);

  useEffect(() => {
    void refreshBooks();
    return () => {
      realtimeClientRef.current?.close();
      recorderRef.current?.reset();
      if (readTimerRef.current) {
        window.clearTimeout(readTimerRef.current);
      }
    };
  }, []);

  const currentPage = selectedBook?.pages[currentPageIndex];
  const currentSentence = currentPage?.sentences[currentSentenceIndex];
  const isConnecting = connectionStatus === 'connecting';
  const isConnected = connectionStatus === 'connected';
  const canStart = connectionStatus === 'idle' || connectionStatus === 'failed' || connectionStatus === 'closed';
  const webSocketState = realtimeClientRef.current?.getWebSocketState() ?? '未创建';
  const keywords = currentSentence?.keywords ?? [];
  const currentWordTokens = useMemo(() => {
    const scored = assessmentResult && assessmentResult.targetText === currentSentence?.english ? assessmentResult.wordResults : null;
    return scored?.length ? scored : tokenizeSentence(currentSentence?.english ?? '').map((token) => ({ ...token, meaning: wordMeaning(token.text, keywords), status: selectedWord?.index === token.index ? 'current' : token.status }));
  }, [assessmentResult, currentSentence?.english, keywords, selectedWord]);
  const hasCurrentSentence = Boolean(currentSentence?.english.trim());
  const isRecordingOrScoring = recordingStatus === 'recording' || recordingStatus === 'uploading' || recordingStatus === 'scoring';
  const canUseReadingControls = isConnected && hasCurrentSentence && !isAiPlaying && !isRecordingOrScoring;
  const recordingStatusLabels: Record<RecordingStatus, string> = { idle: '未录音', recording: '录音中', uploading: '识别中', scoring: '评分中', done: '已完成', failed: '失败' };
  const voiceStyleLabels: Record<VoiceStyle, string> = { professional_female: '专业外教女声', professional_male: '专业外教男声', child_friendly_female: '儿童友好女声', child_friendly_male: '儿童友好男声' };

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
      setAssessmentResult(null);
      setSelectedWord(null);
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
        voiceStyle,
        onPlaybackChange: setIsAiPlaying,
      });
      await client.startMic();
      appendLog('system', 'AI 英语阅读陪练已开始。');
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
    setIsAiPlaying(false);
    appendLog('system', '已结束本次陪练会话。');
  }

  function ensureConnectedForReading(): boolean {
    if (!isConnected) {
      setErrorMessage('请先点击“开始学习这本绘本”，连接 AI 外教声音。');
      return false;
    }
    if (isAiPlaying) {
      setErrorMessage('AI 正在朗读，请稍后再操作。');
      return false;
    }
    if (!currentSentence?.english.trim()) {
      setErrorMessage('当前没有可朗读的句子。');
      return false;
    }
    setErrorMessage('');
    return true;
  }

  function estimatePageHighlight(sentences: Sentence[]) {
    if (readTimerRef.current) window.clearTimeout(readTimerRef.current);
    let index = 0;
    const tick = () => {
      if (index >= sentences.length) return;
      setCurrentSentenceIndex(index);
      const words = sentences[index].english.split(/\s+/).filter(Boolean).length;
      const duration = Math.max(1400, words * 430 + 800);
      index += 1;
      readTimerRef.current = window.setTimeout(tick, duration);
    };
    tick();
  }

  function handleReadPage() {
    if (!isConnected || isAiPlaying) {
      setErrorMessage(isAiPlaying ? 'AI 正在朗读，请稍后再操作。' : '请先点击“开始学习这本绘本”，连接 AI 外教声音。');
      return;
    }
    const sentences = currentPage?.sentences.filter((sentence) => sentence.english.trim()) ?? [];
    if (!sentences.length || !currentPage) {
      setErrorMessage('当前没有可朗读的句子。');
      return;
    }
    realtimeClientRef.current?.readPage(currentPage.pageNo, sentences.map((sentence) => sentence.english), 'normal');
    setReadMode('page');
    estimatePageHighlight(sentences);
    appendLog('reading', '开始整页朗读。');
  }

  function handleReadSentence(speed: 'normal' | 'slow' = 'normal') {
    if (!ensureConnectedForReading()) return;
    realtimeClientRef.current?.readSentence(currentSentence!.english, speed);
    setReadMode(speed === 'slow' ? 'slow' : 'sentence');
    appendLog('reading', speed === 'slow' ? '开始慢速播放当前句。' : '开始逐句播放当前句。');
  }

  function handleRepeat() {
    if (!currentSentence?.english.trim()) {
      setErrorMessage('当前绘本还没有可学习的英文句子，请先编辑并保存。');
      return;
    }
    if (isAiPlaying) {
      setErrorMessage('AI 正在朗读，请稍后再操作。');
      return;
    }
    try {
      realtimeClientRef.current?.repeatSentence(currentSentence);
      setReadMode('repeat');
      appendLog('reading', `重复本句：${currentSentence.english}`);
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
    setAssessmentResult(null);
    setSelectedWord(null);
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

  function handleStopPlayback() {
    realtimeClientRef.current?.stopPlayback();
    setIsAiPlaying(false);
    if (readTimerRef.current) window.clearTimeout(readTimerRef.current);
    appendLog('reading', '暂停播放。');
  }

  function handleVoiceStyleChange(nextStyle: VoiceStyle) {
    setVoiceStyle(nextStyle);
    try {
      realtimeClientRef.current?.updateVoiceStyle(nextStyle);
      appendLog('system', `已切换外教声音：${voiceStyleLabels[nextStyle]}`);
    } catch {
      appendLog('system', `外教声音将在下次连接时生效：${voiceStyleLabels[nextStyle]}`);
    }
  }

  function handleWordClick(token: WordToken) {
    if (!currentSentence?.english.trim()) {
      setErrorMessage('当前没有可朗读的句子。');
      return;
    }
    if (isAiPlaying) {
      setErrorMessage('AI 正在朗读，请稍后再操作。');
      return;
    }
    if (!isConnected) {
      setErrorMessage('请先点击“开始学习这本绘本”，连接 AI 外教声音。');
      return;
    }
    setSelectedWord({ ...token, meaning: token.meaning ?? wordMeaning(token.text, keywords), status: 'current' });
    try {
      realtimeClientRef.current?.readWord(token.text, currentSentence.english);
      setReadMode('word');
      appendLog('reading', `单词点读：${token.text}`);
    } catch (error) {
      setErrorMessage(String(error));
      appendLog('error', String(error));
    }
  }

  async function handleStartRecording() {
    if (!currentSentence?.english.trim()) {
      setErrorMessage('用户未选择句子就录音，请先选择一个英文句子。');
      return;
    }
    if (isAiPlaying) {
      setErrorMessage('AI 正在朗读，请稍后再操作。');
      return;
    }
    try {
      const recorder = new Recorder();
      recorderRef.current = recorder;
      setRecordingResult(null);
      setAssessmentResult(null);
      await recorder.start();
      setRecordingStatus('recording');
      appendLog('recording', '开始录音');
    } catch (error) {
      setRecordingStatus('failed');
      setErrorMessage(String(error));
      appendLog('error', `开始录音失败：${String(error)}`);
    }
  }

  async function handleStopRecording() {
    try {
      const result = await recorderRef.current?.stop();
      if (!result) return;
      setRecordingResult(result);
      setRecordingStatus('idle');
      appendLog('recording', `停止录音，时长 ${(result.durationMs / 1000).toFixed(1)} 秒`);
    } catch (error) {
      setRecordingStatus('failed');
      setErrorMessage(String(error));
      appendLog('error', `停止录音失败：${String(error)}`);
    }
  }

  function handleResetRecording() {
    recorderRef.current?.reset();
    setRecordingResult(null);
    setAssessmentResult(null);
    setRecordingStatus('idle');
    appendLog('recording', '重新录音。');
  }

  async function handleSubmitAssessment() {
    if (!recordingResult || !currentSentence?.english.trim()) {
      setErrorMessage('请先完成当前句子的跟读录音。');
      return;
    }
    try {
      setRecordingStatus('uploading');
      appendLog('assessment', '正在评分');
      setRecordingStatus('scoring');
      const result = await assessReading({
        audio: recordingResult.blob,
        targetText: currentSentence.english,
        bookId: selectedBook?.id,
        pageNo: currentPage?.pageNo,
        sentenceIndex: currentSentenceIndex,
      });
      setAssessmentResult(result);
      setRecordingStatus('done');
      appendLog('assessment', `评分完成：总分 ${result.score.totalScore}`);
      result.issues.filter((issue) => issue.type === 'missed').forEach((issue) => appendLog('correction', `发现漏读词：${issue.targetWord ?? ''}`));
      try {
        realtimeClientRef.current?.sendAssessmentFeedback(result);
        appendLog('assessment', 'AI反馈完成');
      } catch (error) {
        appendLog('error', `模型语音反馈失败：${String(error)}`);
      }
    } catch (error) {
      setRecordingStatus('failed');
      setErrorMessage(`评分失败，请重新录音。${String(error)}`);
      appendLog('error', `评分失败：${String(error)}`);
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
          <div className="progress-row"><span>当前进度：{progressLabel}</span><span>麦克风：{micStatusLabels[micStatus]}</span><span>跟读：{recordingStatusLabels[recordingStatus]}</span></div>
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
            <p className="current-label">英文原句：</p><div className="token-line">{currentWordTokens.length ? currentWordTokens.map((token) => (
              <button key={`${token.index}-${token.text}-${token.status}`} type="button" className={`word-token token-${token.status ?? 'correct'}`} onClick={() => handleWordClick(token)}>{token.text}</button>
            )) : <strong>{currentSentence?.english || '请先编辑英文句子'}</strong>}</div>
            <p className="current-label">中文意思：</p><p className="current-chinese">{currentSentence?.chinese || '请填写中文意思，方便 AI 用中文讲解。'}</p>
            <div className="keyword-list"><p className="current-label">重点词：</p>{keywords.length ? keywords.map((keyword) => <span key={`${keyword.word}-${keyword.meaning}`}>{keyword.word}：{keyword.meaning}</span>) : <em>暂无重点词，可在编辑区补充。</em>}</div>
            {selectedWord && <div className="word-meaning">{selectedWord.text}：{selectedWord.meaning ?? '暂无中文释义，可在重点词中补充。'}</div>}
          </div>

          <div className="learning-section">
            <div className="section-title-row"><h3>朗读控制区</h3><span>当前模式：{readMode ?? '未播放'}</span></div>
            <label className="voice-select">外教声音
              <select value={voiceStyle} onChange={(event) => handleVoiceStyleChange(event.target.value as VoiceStyle)}>
                {Object.entries(voiceStyleLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}
              </select>
            </label>
            <div className="button-row">
              <button type="button" onClick={handleReadPage} disabled={!isConnected || !currentPage?.sentences.length || isAiPlaying || isRecordingOrScoring}>整页朗读</button>
              <button type="button" onClick={() => handleReadSentence('normal')} disabled={!canUseReadingControls}>逐句播放</button>
              <button type="button" onClick={handleRepeat} disabled={!canUseReadingControls}>重复本句</button>
              <button type="button" onClick={() => handleReadSentence('slow')} disabled={!canUseReadingControls}>慢速播放</button>
              <button type="button" className="danger" onClick={handleStopPlayback} disabled={!isAiPlaying}>暂停播放</button>
              <button type="button" onClick={() => moveSentence(-1)} disabled={!isConnected || atFirstSentence}>上一句</button>
              <button type="button" onClick={() => moveSentence(1)} disabled={!isConnected || atLastSentence}>下一句</button>
            </div>
          </div>

          <div className="learning-section">
            <div className="section-title-row"><h3>跟读录音区</h3><span>{recordingStatusLabels[recordingStatus]}</span></div>
            <div className="button-row">
              <button type="button" className="primary" onClick={() => void handleStartRecording()} disabled={isAiPlaying || recordingStatus === 'recording' || recordingStatus === 'uploading' || recordingStatus === 'scoring'}>{recordingStatus === 'done' ? '重新跟读' : '开始跟读'}</button>
              <button type="button" onClick={() => void handleStopRecording()} disabled={recordingStatus !== 'recording'}>停止录音</button>
              <button type="button" onClick={handleResetRecording} disabled={recordingStatus === 'recording' || recordingStatus === 'uploading' || recordingStatus === 'scoring'}>重新录音</button>
              <button type="button" onClick={() => void handleSubmitAssessment()} disabled={!recordingResult || recordingStatus === 'recording' || recordingStatus === 'uploading' || recordingStatus === 'scoring'}>{recordingStatus === 'scoring' ? '正在评分' : '提交评分'}</button>
              {assessmentResult && <button type="button" onClick={() => handleReadSentence('normal')} disabled={!canUseReadingControls}>再读整句</button>}
              {assessmentResult && <button type="button" onClick={() => moveSentence(1)} disabled={!isConnected || atLastSentence}>下一句</button>}
            </div>
            {recordingResult && <p className="help-text">已录音 {(recordingResult.durationMs / 1000).toFixed(1)} 秒，点击“提交评分”查看发音纠正。</p>}
          </div>

          {assessmentResult && <div className="assessment-card">
            <h3>跟读评分</h3>
            <div className="total-score">总分：{assessmentResult.score.totalScore}</div>
            <div className="score-grid">
              <span>准确率：{assessmentResult.score.accuracyScore}</span>
              <span>流利度：{assessmentResult.score.fluencyScore}</span>
              <span>完整度：{assessmentResult.score.completenessScore}</span>
              <span>清晰度：{assessmentResult.score.clarityScore}</span>
            </div>
            <p><strong>识别结果：</strong>{assessmentResult.recognizedText || '识别文本为空，请重新录音。'}</p>
            <div className="issue-list"><strong>问题分析：</strong>{assessmentResult.issues.length ? assessmentResult.issues.map((issue, index) => (
              <div key={`${issue.type}-${issue.wordIndex}-${index}`} className="issue-item">
                <span>{index + 1}. {issue.message}</span>
                <em>{issue.suggestion}</em>
                <button type="button" className="small" onClick={() => issue.targetWord && handleWordClick({ index: issue.wordIndex ?? index, text: issue.targetWord, normalized: issue.targetWord, status: issue.type })}>听标准发音</button>
              </div>
            )) : <p>暂无明显问题，继续保持。</p>}</div>
            <div className="feedback-box">AI 反馈：{assessmentResult.feedbackText}</div>
          </div>}

          <div className="button-row session-row">
            <button type="button" className="primary" onClick={() => void handleStart()} disabled={!canStart || isConnecting || !selectedBook}>{startButtonLabel}</button>
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
