export type RecorderResult = {
  blob: Blob;
  durationMs: number;
  mimeType: string;
};

export class Recorder {
  static readonly minDurationMs = 1000;
  static readonly maxDurationMs = 30000;

  private stream?: MediaStream;
  private mediaRecorder?: MediaRecorder;
  private chunks: BlobPart[] = [];
  private startedAt = 0;
  private stopTimer?: number;

  async start(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      throw new Error('当前浏览器不支持录音，请使用最新版 Chrome、Edge 或 Safari。');
    }
    if (this.mediaRecorder?.state === 'recording') {
      throw new Error('正在录音中，请先停止当前录音。');
    }

    this.stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    const mimeType = pickMimeType();
    this.mediaRecorder = new MediaRecorder(this.stream, mimeType ? { mimeType } : undefined);
    this.chunks = [];
    this.mediaRecorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        this.chunks.push(event.data);
      }
    };
    this.startedAt = Date.now();
    this.mediaRecorder.start();
    this.stopTimer = window.setTimeout(() => {
      if (this.mediaRecorder?.state === 'recording') {
        this.mediaRecorder.stop();
      }
    }, Recorder.maxDurationMs);
  }

  async stop(): Promise<RecorderResult> {
    const recorder = this.mediaRecorder;
    if (!recorder || recorder.state !== 'recording') {
      throw new Error('当前没有正在进行的录音。');
    }

    const stopped = new Promise<void>((resolve) => {
      recorder.onstop = () => resolve();
    });
    recorder.stop();
    await stopped;
    window.clearTimeout(this.stopTimer);
    this.stopTracks();

    const durationMs = Date.now() - this.startedAt;
    if (durationMs < Recorder.minDurationMs) {
      throw new Error('录音时间太短，请至少读 1 秒。');
    }
    const mimeType = recorder.mimeType || 'audio/webm';
    return { blob: new Blob(this.chunks, { type: mimeType }), durationMs, mimeType };
  }

  reset(): void {
    if (this.mediaRecorder?.state === 'recording') {
      this.mediaRecorder.stop();
    }
    window.clearTimeout(this.stopTimer);
    this.stopTracks();
    this.chunks = [];
    this.mediaRecorder = undefined;
    this.startedAt = 0;
  }

  private stopTracks(): void {
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = undefined;
  }
}

function pickMimeType(): string {
  const candidates = ['audio/webm;codecs=opus', 'audio/webm', 'audio/wav', 'audio/mp4'];
  return candidates.find((type) => MediaRecorder.isTypeSupported(type)) ?? '';
}
