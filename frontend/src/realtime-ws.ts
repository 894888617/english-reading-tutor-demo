import type { ReadingAssessmentResult, VoiceStyle } from './analysis/pronunciationDiff';
import type { Book, Sentence } from './story';

export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'failed' | 'closed';
export type MicStatus = 'idle' | 'requesting' | 'recording' | 'stopped' | 'failed';
export type ResponseState = { hasActiveResponse: boolean; activeResponseId: string | null };

export interface RealtimeDebugEvent {
  kind: 'system' | 'reading' | 'recording' | 'assessment' | 'correction' | 'status' | 'websocket' | 'event' | 'text' | 'transcript' | 'audio' | 'error';
  message: string;
  raw?: unknown;
}

export interface RealtimeWsOptions {
  book: Book;
  pageNo: number;
  currentSentence: Sentence;
  url?: string;
  onConnectionStatusChange?: (status: ConnectionStatus) => void;
  onMicStatusChange?: (status: MicStatus) => void;
  onDebugEvent?: (event: RealtimeDebugEvent) => void;
  voiceStyle?: VoiceStyle;
  onPlaybackChange?: (playing: boolean) => void;
}


type ControlMessage =
  | { type: 'start_lesson'; book: Pick<Book, 'id' | 'title' | 'englishTitle' | 'level'>; pageNo: number; currentSentence: Sentence; voiceStyle?: VoiceStyle }
  | { type: 'session_update'; voiceStyle: VoiceStyle }
  | { type: 'update_sentence'; book: Pick<Book, 'id' | 'title' | 'englishTitle' | 'level'>; pageNo: number; currentSentence: Sentence }
  | { type: 'read_page'; pageNo: number; sentences: string[]; speed: 'normal' | 'slow'; voiceStyle?: VoiceStyle }
  | { type: 'read_sentence'; sentence: string; speed: 'normal' | 'slow'; voiceStyle?: VoiceStyle }
  | { type: 'repeat_sentence'; sentence: string; currentSentence?: Sentence; voiceStyle?: VoiceStyle }
  | { type: 'read_word'; word: string; sentence: string; voiceStyle?: VoiceStyle }
  | { type: 'assessment_feedback'; result: ReadingAssessmentResult; voiceStyle?: VoiceStyle }
  | { type: 'stop_playback' }
  | { type: 'stop' };

const TARGET_SAMPLE_RATE = 16000;
const CHUNK_SAMPLES = 1600;

export class RealtimeWsClient {
  private ws?: WebSocket;
  private options?: RealtimeWsOptions;
  private mediaStream?: MediaStream;
  private audioContext?: AudioContext;
  private micSource?: MediaStreamAudioSourceNode;
  private processor?: ScriptProcessorNode;
  private pendingInputSamples: number[] = [];
  private playbackContext?: AudioContext;
  private nextPlaybackTime = 0;
  private playbackSources: AudioBufferSourceNode[] = [];
  private responseState: ResponseState = { hasActiveResponse: false, activeResponseId: null };

  async connect(options: RealtimeWsOptions): Promise<void> {
    this.options = options;
    this.emitConnectionStatus('connecting');
    const WS_URL =
      window.location.protocol === "https:"
        ? `wss://${window.location.host}/ws/realtime`
        : `ws://${window.location.host}/ws/realtime`;
    const url = options.url ?? WS_URL;

    await new Promise<void>((resolve, reject) => {
      const ws = new WebSocket(url);
      this.ws = ws;
      ws.binaryType = 'arraybuffer';

      const timeoutId = window.setTimeout(() => {
        reject(new Error('连接 Java 实时语音 WebSocket 超时。'));
        ws.close();
      }, 15000);

      ws.onopen = () => {
        window.clearTimeout(timeoutId);
        this.emitDebug('status', '连接成功');
        this.emitConnectionStatus('connected');
        this.sendControlMessage({
          type: 'start_lesson',
          book: bookContext(options.book),
          pageNo: options.pageNo,
          currentSentence: options.currentSentence,
          voiceStyle: options.voiceStyle,
        });
        resolve();
      };
      ws.onerror = () => {
        window.clearTimeout(timeoutId);
        this.emitConnectionStatus('failed');
        reject(new Error('浏览器 WebSocket 连接失败，请确认后端服务和 /ws/realtime 代理已启动。'));
      };
      ws.onclose = () => {
        this.emitDebug('websocket', '浏览器 WebSocket 已关闭。');
        this.stopMic();
        this.clearPlaybackQueue();
        this.emitConnectionStatus('closed');
      };
      ws.onmessage = (event) => this.handleMessage(event.data);
    });
  }

  async startMic(): Promise<void> {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('WebSocket 连接成功后才能开启麦克风。');
    }

    this.emitMicStatus('requesting');
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.audioContext = new AudioContext();
      this.micSource = this.audioContext.createMediaStreamSource(this.mediaStream);
      this.processor = this.audioContext.createScriptProcessor(4096, 1, 1);
      this.processor.onaudioprocess = (event) => this.handleMicAudio(event.inputBuffer);
      this.micSource.connect(this.processor);
      this.processor.connect(this.audioContext.destination);
      this.emitMicStatus('recording');
      this.emitDebug('status', '麦克风已开启，正在向 Java 后端发送 16 kHz PCM 音频。');
    } catch (error) {
      this.emitMicStatus('failed');
      this.emitDebug('error', `麦克风授权或采集失败：${String(error)}`);
      this.stopMic(false);
      throw error;
    }
  }

  stopMic(emitStatus = true): void {
    this.processor?.disconnect();
    this.processor = undefined;
    this.micSource?.disconnect();
    this.micSource = undefined;
    this.mediaStream?.getTracks().forEach((track) => track.stop());
    this.mediaStream = undefined;
    void this.audioContext?.close().catch(() => undefined);
    this.audioContext = undefined;
    this.pendingInputSamples = [];
    if (emitStatus) {
      this.emitMicStatus('stopped');
    }
  }

  sendControlMessage(message: ControlMessage): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      throw new Error('实时语音 WebSocket 尚未打开。');
    }
    this.ws.send(JSON.stringify(message));
  }

  updateSentence(book: Book, pageNo: number, currentSentence: Sentence): void {
    this.sendControlMessage({ type: 'update_sentence', book: bookContext(book), pageNo, currentSentence });
  }

  updateVoiceStyle(voiceStyle: VoiceStyle): void {
    this.options = this.options ? { ...this.options, voiceStyle } : this.options;
    this.sendControlMessage({ type: 'session_update', voiceStyle });
  }

  readPage(pageNo: number, sentences: string[], speed: 'normal' | 'slow' = 'normal'): void {
    this.prepareForNewResponse();
    this.sendControlMessage({ type: 'read_page', pageNo, sentences, speed, voiceStyle: this.options?.voiceStyle });
  }

  readSentence(sentence: string, speed: 'normal' | 'slow' = 'normal'): void {
    this.prepareForNewResponse();
    this.sendControlMessage({ type: 'read_sentence', sentence, speed, voiceStyle: this.options?.voiceStyle });
  }

  repeatSentence(currentSentence: Sentence): void {
    this.prepareForNewResponse();
    this.sendControlMessage({ type: 'repeat_sentence', sentence: currentSentence.english, currentSentence, voiceStyle: this.options?.voiceStyle });
  }

  readWord(word: string, sentence: string): void {
    this.prepareForNewResponse();
    this.sendControlMessage({ type: 'read_word', word, sentence, voiceStyle: this.options?.voiceStyle });
  }

  sendAssessmentFeedback(result: ReadingAssessmentResult): void {
    this.prepareForNewResponse();
    this.sendControlMessage({ type: 'assessment_feedback', result, voiceStyle: this.options?.voiceStyle });
  }

  stopPlayback(): void {
    this.clearPlaybackQueue();
    if (this.responseState.hasActiveResponse) {
      this.sendControlMessage({ type: 'stop_playback' });
      this.responseState = { hasActiveResponse: false, activeResponseId: null };
    }
  }

  close(): void {
    try {
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.sendControlMessage({ type: 'stop' });
      }
    } catch (error) {
      this.emitDebug('error', `发送结束会话消息失败：${String(error)}`);
    }
    this.stopMic();
    this.clearPlaybackQueue();
    this.ws?.close();
    this.ws = undefined;
  }

  getWebSocketState(): string {
    if (!this.ws) {
      return '未创建';
    }
    return ['连接中', '已打开', '关闭中', '已关闭'][this.ws.readyState] ?? String(this.ws.readyState);
  }

  private handleMicAudio(inputBuffer: AudioBuffer): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN || !this.audioContext) {
      return;
    }
    const channel = inputBuffer.getChannelData(0);
    const resampled = downsampleFloat32(channel, this.audioContext.sampleRate, TARGET_SAMPLE_RATE);
    for (const sample of resampled) {
      this.pendingInputSamples.push(sample);
    }
    while (this.pendingInputSamples.length >= CHUNK_SAMPLES) {
      const chunk = this.pendingInputSamples.splice(0, CHUNK_SAMPLES);
      this.ws.send(floatTo16BitPcm(chunk));

    }
  }

  private handleMessage(data: string | ArrayBuffer | Blob): void {
    if (data instanceof ArrayBuffer) {
      this.enqueuePcmPlayback(data);
      return;
    }
    if (data instanceof Blob) {
      void data.arrayBuffer().then((buffer) => this.enqueuePcmPlayback(buffer));
      return;
    }

    try {
      const event = JSON.parse(data);
      const type = event.type ?? 'unknown';
      if (type === 'playback_started') {
        this.responseState = { hasActiveResponse: true, activeResponseId: null };
        this.options?.onPlaybackChange?.(true);
        this.emitDebug('reading', event.message ?? 'AI 开始朗读。', event);
        return;
      }
      if (type === 'playback_done') {
        this.responseState = { hasActiveResponse: false, activeResponseId: null };
        this.options?.onPlaybackChange?.(false);
        this.emitDebug('reading', event.message ?? 'AI 朗读完成。', event);
        return;
      }
      if (type === 'ai_text_delta' && typeof event.text === 'string') {
        this.emitDebug('text', event.text, event);
        return;
      }
      if (type === 'ai_text_done' && typeof event.text === 'string') {
        this.emitDebug('text', event.text || 'AI 老师回复完成。', event);
        return;
      }
      if (type === 'error') {
        this.responseState = { hasActiveResponse: false, activeResponseId: null };
        this.emitDebug('error', event.message ?? '未知实时语音错误。', event);
        return;
      }
      if (type === 'dashscope_event') {
        const innerType = event.event?.type ?? 'unknown';
        this.emitDebug('event', `模型事件：${innerType}`, event.event);
        const transcript = event.event?.transcript ?? event.event?.item?.content?.[0]?.transcript;
        if (typeof transcript === 'string' && transcript) {
          this.emitDebug('transcript', transcript, event.event);
        }
        return;
      }
      this.emitDebug('status', event.message ?? `实时事件：${type}`, event);
    } catch (error) {
      this.emitDebug('event', `收到非 JSON WebSocket 消息：${String(data)}`, error);
    }
  }

  private prepareForNewResponse(): void {
    if (!this.responseState.hasActiveResponse) {
      this.clearPlaybackQueue();
      return;
    }
    this.clearPlaybackQueue();
    this.sendControlMessage({ type: 'stop_playback' });
    this.responseState = { hasActiveResponse: false, activeResponseId: null };
  }

  private enqueuePcmPlayback(arrayBuffer: ArrayBuffer): void {
    const samples = new Int16Array(arrayBuffer);
    if (samples.length === 0) {
      return;
    }
    const context = this.getPlaybackContext();
    const audioBuffer = context.createBuffer(1, samples.length, TARGET_SAMPLE_RATE);
    const output = audioBuffer.getChannelData(0);
    for (let index = 0; index < samples.length; index += 1) {
      output[index] = Math.max(-1, Math.min(1, samples[index] / 32768));
    }

    const source = context.createBufferSource();
    source.buffer = audioBuffer;
    source.connect(context.destination);
    source.onended = () => {
      this.playbackSources = this.playbackSources.filter((item) => item !== source);
    };

    const startAt = Math.max(context.currentTime + 0.02, this.nextPlaybackTime);
    source.start(startAt);
    this.nextPlaybackTime = startAt + audioBuffer.duration;
    this.playbackSources.push(source);

  }

  private getPlaybackContext(): AudioContext {
    if (!this.playbackContext || this.playbackContext.state === 'closed') {
      this.playbackContext = new AudioContext({ sampleRate: TARGET_SAMPLE_RATE });
      this.nextPlaybackTime = this.playbackContext.currentTime;
    }
    if (this.playbackContext.state === 'suspended') {
      void this.playbackContext.resume();
    }
    return this.playbackContext;
  }

  private clearPlaybackQueue(): void {
    this.playbackSources.forEach((source) => {
      try {
        source.stop();
      } catch {
        // Source may already have ended.
      }
    });
    this.playbackSources = [];
    this.options?.onPlaybackChange?.(false);
    this.nextPlaybackTime = 0;
    void this.playbackContext?.close().catch(() => undefined);
    this.playbackContext = undefined;
  }

  private emitConnectionStatus(status: ConnectionStatus): void {
    this.options?.onConnectionStatusChange?.(status);
  }

  private emitMicStatus(status: MicStatus): void {
    this.options?.onMicStatusChange?.(status);
  }

  private emitDebug(kind: RealtimeDebugEvent['kind'], message: string, raw?: unknown): void {
    this.options?.onDebugEvent?.({ kind, message, raw });
  }
}

function downsampleFloat32(input: Float32Array, sourceRate: number, targetRate: number): number[] {
  if (sourceRate === targetRate) {
    return Array.from(input);
  }
  const ratio = sourceRate / targetRate;
  const outputLength = Math.floor(input.length / ratio);
  const output: number[] = [];
  for (let index = 0; index < outputLength; index += 1) {
    const start = Math.floor(index * ratio);
    const end = Math.min(Math.floor((index + 1) * ratio), input.length);
    let sum = 0;
    for (let sourceIndex = start; sourceIndex < end; sourceIndex += 1) {
      sum += input[sourceIndex];
    }
    output.push(sum / Math.max(1, end - start));
  }
  return output;
}

function floatTo16BitPcm(samples: number[]): ArrayBuffer {
  const buffer = new ArrayBuffer(samples.length * 2);
  const view = new DataView(buffer);
  for (let index = 0; index < samples.length; index += 1) {
    const sample = Math.max(-1, Math.min(1, samples[index]));
    view.setInt16(index * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
  }
  return buffer;
}

function bookContext(book: Book): Pick<Book, 'id' | 'title' | 'englishTitle' | 'level'> {
  return {
    id: book.id,
    title: book.title,
    englishTitle: book.englishTitle,
    level: book.level,
  };
}
