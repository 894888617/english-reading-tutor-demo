import { useEffect, useMemo, useRef, useState } from 'react';
import { getStory, saveLog } from './api';
import { RealtimeWsClient, type ConnectionStatus, type MicStatus, type RealtimeDebugEvent } from './realtime-ws';
import { fallbackStory, type StoryResponse } from './story';

type DebugLog = {
  id: number;
  time: string;
  kind: RealtimeDebugEvent['kind'] | 'ui';
  message: string;
};

function nowLabel(): string {
  return new Date().toLocaleTimeString();
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
        appendLog('ui', `Story loaded: ${loadedStory.title}`);
      })
      .catch((error) => {
        setStory(fallbackStory);
        setErrorMessage(String(error));
        appendLog('error', `Using fallback story because backend story API failed: ${String(error)}`);
      });

    return () => {
      realtimeClientRef.current?.close();
    };
  }, []);

  const currentPage = story?.pages[currentPageIndex];
  const currentSentence = currentPage?.sentences[currentSentenceIndex] ?? '';
  const isConnecting = connectionStatus === 'connecting';
  const isConnected = connectionStatus === 'connected';
  const canStart = connectionStatus === 'idle' || connectionStatus === 'failed' || connectionStatus === 'closed';
  const webSocketState = realtimeClientRef.current?.getWebSocketState() ?? 'not-created';

  const progressLabel = useMemo(() => {
    if (!story || !currentPage) {
      return 'Loading...';
    }
    return `Page ${currentPage.page} · Sentence ${currentSentenceIndex + 1}/${currentPage.sentences.length}`;
  }, [story, currentPage, currentSentenceIndex]);

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
      }).catch((error) => appendLog('error', `Failed to save transcript log: ${String(error)}`));
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
        currentSentence,
        onConnectionStatusChange: setConnectionStatus,
        onMicStatusChange: setMicStatus,
        onDebugEvent: handleRealtimeDebug,
      });
      await client.startMic();
      appendLog('ui', 'AI tutor WebSocket session started.');
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
    appendLog('ui', 'Stopped the tutor session.');
  }

  function handleRepeat() {
    if (!currentSentence) {
      return;
    }
    try {
      realtimeClientRef.current?.repeatSentence(currentSentence);
      appendLog('ui', `Asked AI to repeat: ${currentSentence}`);
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
    appendLog('ui', `Moved to sentence: ${nextSentence}`);

    if (isConnected) {
      try {
        realtimeClientRef.current?.updateSentence(story.title, nextSentence);
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
          <p className="eyebrow">Realtime WebSocket Demo</p>
          <h1>AI English Reading Tutor Demo</h1>
          <p className="subtitle">A simple H5 reading coach powered by a Java WebSocket proxy and Qwen3.5 Omni realtime voice.</p>
        </div>
        <div className={`status-pill status-${connectionStatus}`}>{connectionStatus}</div>
      </section>

      <section className="content-grid">
        <article className="story-panel card">
          <div className="story-header">
            <div>
              <p className="section-label">Story</p>
              <h2>{story?.title ?? 'Loading story...'}</h2>
            </div>
            <span className="level-badge">{story?.level ?? '...'}</span>
          </div>

          <div className="progress-row">
            <span>{progressLabel}</span>
            <span>Microphone: {micStatus}</span>
          </div>

          <div className="page-card">
            {currentPage?.sentences.map((sentence, index) => (
              <p
                key={`${currentPage.page}-${sentence}`}
                className={index === currentSentenceIndex ? 'sentence active' : 'sentence'}
              >
                <span className="sentence-number">{index + 1}</span>
                {sentence}
              </p>
            ))}
          </div>

          <div className="current-sentence-box">
            <span>Current sentence</span>
            <strong>{currentSentence || 'Loading...'}</strong>
          </div>

          <div className="button-row">
            <button type="button" onClick={() => moveSentence(-1)} disabled={!isConnected || atFirstSentence}>
              Previous Sentence
            </button>
            <button type="button" onClick={() => moveSentence(1)} disabled={!isConnected || atLastSentence}>
              Next Sentence
            </button>
            <button type="button" className="primary" onClick={handleStart} disabled={!canStart || isConnecting || !story}>
              {isConnecting ? 'Connecting...' : 'Start Tutor'}
            </button>
            <button type="button" onClick={handleRepeat} disabled={!isConnected}>
              Repeat Reading
            </button>
            <button type="button" className="danger" onClick={handleStop} disabled={!isConnected && !isConnecting}>
              Stop Session
            </button>
          </div>

          {errorMessage && <div className="error-box">{errorMessage}</div>}
        </article>

        <aside className="debug-panel card">
          <div className="debug-header">
            <div>
              <p className="section-label">Debug</p>
              <h2>Conversation Events</h2>
            </div>
          </div>

          <dl className="debug-stats">
            <div>
              <dt>Connection</dt>
              <dd>{connectionStatus}</dd>
            </div>
            <div>
              <dt>WebSocket</dt>
              <dd>{webSocketState}</dd>
            </div>
            <div>
              <dt>Microphone</dt>
              <dd>{micStatus}</dd>
            </div>
          </dl>

          <div className="log-list" aria-live="polite">
            {logs.length === 0 ? (
              <p className="empty-log">No events yet. Click Start Tutor to request microphone access and connect.</p>
            ) : (
              logs.map((log) => (
                <div key={log.id} className={`log-item log-${log.kind}`}>
                  <span>{log.time}</span>
                  <strong>{log.kind}</strong>
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
