import { useEffect, useMemo, useRef, useState } from 'react';
import { getStory, saveLog } from './api';
import { RealtimeWsClient, type ConnectionStatus, type MicStatus, type RealtimeDebugEvent } from './realtime-ws';
import { fallbackStory, type Sentence, type StoryResponse } from './story';

type DebugLog = {
  id: number;
  time: string;
  kind: RealtimeDebugEvent['kind'] | 'ui';
  message: string;
};

type Keyword = {
  word: string;
  meaning: string;
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

function getKeywords(sentence?: Sentence): Keyword[] {
  if (!sentence) {
    return [];
  }

  const text = sentence.english.toLowerCase();
  const candidates: Keyword[] = [
    { word: 'rabbit', meaning: '小兔子' },
    { word: 'red hat', meaning: '红帽子' },
    { word: 'looking for', meaning: '正在寻找' },
    { word: 'bird', meaning: '鸟儿' },
    { word: 'under the tree', meaning: '在树下面' },
    { word: 'finds', meaning: '找到了' },
    { word: 'asks', meaning: '询问' },
  ];

  return candidates.filter((item) => text.includes(item.word)).slice(0, 2);
}

function App() {
  const [story, setStory] = useState<StoryResponse | null>(null);
  const [currentPageIndex, setCurrentPageIndex] = useState(0);
  const [currentSentenceIndex, setCurrentSentenceIndex] = useState(0);
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>('idle');
  const [micStatus, setMicStatus] = useState<MicStatus>('idle');
  const [logs, setLogs] = useState<DebugLog[]>([]);
  const [errorMessage, setErrorMessage] = useState('');
  const realtimeClientRef = useRef<RealtimeWsClient | null>(null);
  const logIdRef = useRef(1);

  useEffect(() => {
    getStory()
      .then((loadedStory) => {
        setStory(loadedStory);
        appendLog('ui', `故事已加载：${loadedStory.title}`);
      })
      .catch((error) => {
        setStory(fallbackStory);
        setErrorMessage(`故事接口加载失败，已使用本地故事：${String(error)}`);
        appendLog('error', `故事接口加载失败，已使用本地故事：${String(error)}`);
      });

    return () => {
      realtimeClientRef.current?.close();
    };
  }, []);

  const currentPage = story?.pages[currentPageIndex];
  const currentSentence = currentPage?.sentences[currentSentenceIndex];
  const isConnecting = connectionStatus === 'connecting';
  const isConnected = connectionStatus === 'connected';
  const canStart = connectionStatus === 'idle' || connectionStatus === 'failed' || connectionStatus === 'closed';
  const webSocketState = realtimeClientRef.current?.getWebSocketState() ?? '未创建';
  const keywords = useMemo(() => getKeywords(currentSentence), [currentSentence]);

  const progressLabel = useMemo(() => {
    if (!story || !currentPage) {
      return '加载中……';
    }
    return `第 ${currentPage.page} 页 · 第 ${currentSentenceIndex + 1}/${currentPage.sentences.length} 句`;
  }, [story, currentPage, currentSentenceIndex]);

  const startButtonLabel = useMemo(() => {
    if (isConnecting) {
      return '正在连接';
    }
    if (isConnected) {
      return '陪练中';
    }
    return '开始陪练';
  }, [isConnecting, isConnected]);

  const stopButtonLabel = connectionStatus === 'closed' ? '已结束' : '结束会话';

  function appendLog(kind: DebugLog['kind'], message: string) {
    setLogs((currentLogs) => [
      {
        id: logIdRef.current++,
        time: nowLabel(),
        kind,
        message,
      },
      ...currentLogs,
    ].slice(0, 100));
  }

  function handleRealtimeDebug(event: RealtimeDebugEvent) {
    appendLog(event.kind, event.message);
    if (event.kind === 'error') {
      setErrorMessage(event.message);
    }
    if (event.kind === 'transcript' && story && currentPage) {
      void saveLog({
        role: 'student',
        content: event.message,
        page: currentPage.page,
        sentenceIndex: currentSentenceIndex,
        timestamp: new Date().toISOString().slice(0, 19),
      }).catch((error) => appendLog('error', `保存学生发言记录失败：${String(error)}`));
    }
  }

  async function handleStart() {
    if (!story || !currentSentence) {
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
        storyTitle: story.title,
        englishTitle: story.englishTitle,
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
    if (!currentSentence) {
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
    if (!story) {
      return;
    }

    let nextPageIndex = currentPageIndex;
    let nextSentenceIndex = currentSentenceIndex + direction;

    if (direction > 0 && currentPage && nextSentenceIndex >= currentPage.sentences.length) {
      nextPageIndex = Math.min(currentPageIndex + 1, story.pages.length - 1);
      nextSentenceIndex = nextPageIndex === currentPageIndex ? currentSentenceIndex : 0;
    }

    if (direction < 0 && nextSentenceIndex < 0) {
      nextPageIndex = Math.max(currentPageIndex - 1, 0);
      nextSentenceIndex = nextPageIndex === currentPageIndex
        ? currentSentenceIndex
        : story.pages[nextPageIndex].sentences.length - 1;
    }

    setCurrentPageIndex(nextPageIndex);
    setCurrentSentenceIndex(nextSentenceIndex);

    const nextSentence = story.pages[nextPageIndex].sentences[nextSentenceIndex];
    appendLog('ui', `已切换到新句子：${nextSentence.english}`);

    if (isConnected) {
      try {
        realtimeClientRef.current?.updateSentence(story.title, story.englishTitle, nextSentence);
      } catch (error) {
        setErrorMessage(String(error));
        appendLog('error', String(error));
      }
    }
  }

  const atFirstSentence = currentPageIndex === 0 && currentSentenceIndex === 0;
  const atLastSentence = story
    ? currentPageIndex === story.pages.length - 1 && currentSentenceIndex === story.pages[currentPageIndex].sentences.length - 1
    : true;

  return (
    <main className="app-shell">
      <section className="hero-card">
        <div>
          <p className="eyebrow">中文讲解 + 英文朗读 + 实时语音互动</p>
          <h1>AI 英语阅读导师演示</h1>
          <p className="subtitle">一个由 Java 后端代理和 Qwen3.5 Omni 实时语音驱动的中文讲解式英语阅读练习 Demo。</p>
        </div>
        <div className={`status-pill status-${connectionStatus}`}>{connectionStatusLabels[connectionStatus]}</div>
      </section>

      <section className="content-grid">
        <article className="story-panel card">
          <div className="story-header">
            <div>
              <p className="section-label">故事</p>
              <h2>{story ? `故事：${story.title}` : '正在加载故事……'}</h2>
              {story?.englishTitle && <p className="english-title">英文名：{story.englishTitle}</p>}
            </div>
            <span className="level-badge">等级：{story?.level ?? '……'}</span>
          </div>

          <div className="progress-row">
            <span>当前进度：{progressLabel}</span>
            <span>麦克风：{micStatusLabels[micStatus]}</span>
          </div>

          <div className="page-card">
            {currentPage?.sentences.map((sentence, index) => (
              <div
                key={`${currentPage.page}-${sentence.english}`}
                className={index === currentSentenceIndex ? 'sentence active' : 'sentence'}
              >
                <span className="sentence-number">{index + 1}</span>
                <div>
                  <p className="sentence-english">{sentence.english}</p>
                  <p className="sentence-chinese">{sentence.chinese}</p>
                </div>
              </div>
            ))}
          </div>

          <div className="current-sentence-box">
            <span>当前句子</span>
            <p className="current-label">英文原句：</p>
            <strong>{currentSentence?.english ?? '加载中……'}</strong>
            <p className="current-label">中文意思：</p>
            <p className="current-chinese">{currentSentence?.chinese ?? '加载中……'}</p>
            {keywords.length > 0 && (
              <div className="keyword-list">
                <p className="current-label">重点词：</p>
                {keywords.map((keyword) => (
                  <span key={keyword.word}>{keyword.word}：{keyword.meaning}</span>
                ))}
              </div>
            )}
          </div>

          <div className="button-row">
            <button type="button" onClick={() => moveSentence(-1)} disabled={!isConnected || atFirstSentence}>
              上一句
            </button>
            <button type="button" onClick={() => moveSentence(1)} disabled={!isConnected || atLastSentence}>
              下一句
            </button>
            <button type="button" className="primary" onClick={handleStart} disabled={!canStart || isConnecting || !story}>
              {startButtonLabel}
            </button>
            <button type="button" onClick={handleRepeat} disabled={!isConnected}>
              重复朗读
            </button>
            <button type="button" className="danger" onClick={handleStop} disabled={!isConnected && !isConnecting}>
              {stopButtonLabel}
            </button>
          </div>

          {errorMessage && <div className="error-box">错误：{errorMessage}</div>}
        </article>

        <aside className="debug-panel card">
          <div className="debug-header">
            <div>
              <p className="section-label">对话记录</p>
              <h2>对话活动</h2>
            </div>
          </div>

          <dl className="debug-stats">
            <div>
              <dt>连接状态</dt>
              <dd>{connectionStatusLabels[connectionStatus]}</dd>
            </div>
            <div>
              <dt>数据通道</dt>
              <dd>{webSocketState}</dd>
            </div>
            <div>
              <dt>麦克风</dt>
              <dd>{micStatusLabels[micStatus]}</dd>
            </div>
          </dl>

          <div className="log-list" aria-live="polite">
            {logs.length === 0 ? (
              <p className="empty-log">还没有对话活动。点击“开始陪练”，允许麦克风权限后开始学习。</p>
            ) : (
              logs.map((log) => (
                <div key={log.id} className={`log-item log-${log.kind}`}>
                  <span>{log.time}</span>
                  <strong>{logKindLabels[log.kind]}</strong>
                  <p>{log.message}</p>
                </div>
              ))
            )}
          </div>
        </aside>
      </section>
    </main>
  );
}

export default App;
