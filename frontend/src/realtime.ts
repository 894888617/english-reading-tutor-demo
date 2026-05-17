import { exchangeSdp } from './api';

export type ConnectionStatus = 'idle' | 'connecting' | 'connected' | 'failed' | 'closed';

export interface RealtimeDebugEvent {
  kind: 'status' | 'datachannel' | 'event' | 'text' | 'transcript' | 'error';
  message: string;
  raw?: unknown;
}

export interface RealtimeStartOptions {
  storyTitle: string;
  currentSentence: string;
  onStatusChange?: (status: ConnectionStatus) => void;
  onMicChange?: (enabled: boolean) => void;
  onDebugEvent?: (event: RealtimeDebugEvent) => void;
}

export function buildTutorInstructions(storyTitle: string, currentSentence: string): string {
  return `You are an English reading tutor for Chinese children aged 6-10.

Current story:
${storyTitle}

Current sentence:
${currentSentence}

Your job:
1. Read the current sentence clearly and naturally.
2. Help the student understand the story.
3. Explain difficult words using simple English.
4. If the student still does not understand, explain briefly in Chinese.
5. Ask one simple question at a time.
6. Correct mistakes gently.
7. Encourage the student to speak English.
8. Keep your answer short, natural, and teacher-like.

Rules:
- Stay focused on the current story.
- Do not talk about unrelated topics.
- Do not give long grammar lessons.
- Use simple English.
- For young learners, speak slowly and clearly.
- If the student answers incorrectly, first encourage them, then give the correct answer.
- Do not ask multiple questions at once.`;
}

export class RealtimeClient {
  private peerConnection?: RTCPeerConnection;
  private dataChannel?: RTCDataChannel;
  private localStream?: MediaStream;
  private remoteAudio?: HTMLAudioElement;
  private options?: RealtimeStartOptions;
  private storyTitle = '';

  async start(options: RealtimeStartOptions): Promise<void> {
    this.options = options;
    this.storyTitle = options.storyTitle;
    this.emitStatus('connecting');

    try {
      this.peerConnection = new RTCPeerConnection({
        iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
      });

      this.peerConnection.onconnectionstatechange = () => {
        this.emitDebug('status', `PeerConnection: ${this.peerConnection?.connectionState}`);
        if (this.peerConnection?.connectionState === 'failed') {
          this.emitStatus('failed');
        }
      };

      this.peerConnection.oniceconnectionstatechange = () => {
        this.emitDebug('status', `ICE: ${this.peerConnection?.iceConnectionState}`);
      };

      this.peerConnection.ontrack = (event) => {
        this.emitDebug('event', 'Received remote AI audio track.');
        const [stream] = event.streams;
        if (!this.remoteAudio) {
          this.remoteAudio = new Audio();
          this.remoteAudio.autoplay = true;
        }
        this.remoteAudio.srcObject = stream;
        void this.remoteAudio.play().catch((error) => {
          this.emitDebug('error', `Audio playback blocked: ${String(error)}`);
        });
      };

      this.localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
      this.options?.onMicChange?.(true);
      this.localStream.getAudioTracks().forEach((track) => {
        this.peerConnection?.addTrack(track, this.localStream!);
      });

      this.dataChannel = this.peerConnection.createDataChannel('realtime-events');
      this.installDataChannelHandlers();

      const offer = await this.peerConnection.createOffer();
      await this.peerConnection.setLocalDescription(offer);
      await this.waitForIceGatheringComplete();

      const localSdp = this.peerConnection.localDescription?.sdp;
      if (!localSdp) {
        throw new Error('Browser did not create a local Offer SDP.');
      }

      this.emitDebug('status', `Created Offer SDP, length=${localSdp.length}`);
      const answerSdp = await exchangeSdp(localSdp);
      this.emitDebug('status', `Received Answer SDP, length=${answerSdp.length}`);
      await this.peerConnection.setRemoteDescription({ type: 'answer', sdp: answerSdp });

      await this.waitForDataChannelOpen();
      this.sendSessionUpdate(options.currentSentence, options.storyTitle);
      this.sendTextMessage('Please start the reading lesson. Read the current sentence first, then ask me one simple question.');
      this.emitStatus('connected');
    } catch (error) {
      this.emitDebug('error', String(error));
      this.cleanup(false);
      this.emitStatus('failed');
      throw error;
    }
  }

  stop(): void {
    this.cleanup(true);
  }

  private cleanup(emitClosed: boolean): void {
    this.dataChannel?.close();
    this.dataChannel = undefined;

    this.localStream?.getTracks().forEach((track) => track.stop());
    this.localStream = undefined;
    this.options?.onMicChange?.(false);

    this.peerConnection?.getSenders().forEach((sender) => sender.track?.stop());
    this.peerConnection?.close();
    this.peerConnection = undefined;

    if (this.remoteAudio) {
      this.remoteAudio.pause();
      this.remoteAudio.srcObject = null;
      this.remoteAudio = undefined;
    }

    if (emitClosed) {
      this.emitStatus('closed');
    }
    this.emitDebug('status', 'Realtime session closed and microphone released.');
  }

  close(): void {
    this.stop();
  }

  sendSessionUpdate(currentSentence: string, storyTitle = this.storyTitle): void {
    this.storyTitle = storyTitle;
    this.sendEvent({
      type: 'session.update',
      session: {
        modalities: ['text', 'audio'],
        voice: 'Ethan',
        instructions: buildTutorInstructions(storyTitle, currentSentence),
        turn_detection: {
          type: 'semantic_vad',
          threshold: 0.5,
          silence_duration_ms: 800,
        },
        temperature: 0.7,
      },
    });
  }

  sendTextMessage(text: string): void {
    this.sendEvent({
      type: 'conversation.item.create',
      item: {
        type: 'message',
        role: 'user',
        content: [
          {
            type: 'input_text',
            text,
          },
        ],
      },
    });
    this.sendEvent({ type: 'response.create' });
  }

  repeatCurrentSentence(currentSentence: string): void {
    this.sendTextMessage(`Please read the current sentence slowly and clearly: ${currentSentence}`);
  }

  getDataChannelState(): string {
    return this.dataChannel?.readyState ?? 'not-created';
  }

  private installDataChannelHandlers(): void {
    if (!this.dataChannel) {
      return;
    }

    this.dataChannel.onopen = () => {
      this.emitDebug('datachannel', `DataChannel opened: ${this.dataChannel?.label}`);
    };
    this.dataChannel.onclose = () => {
      this.emitDebug('datachannel', 'DataChannel closed.');
    };
    this.dataChannel.onerror = () => {
      this.emitDebug('error', 'DataChannel error. Check browser console for details.');
    };
    this.dataChannel.onmessage = (messageEvent) => {
      this.handleDataChannelMessage(messageEvent.data);
    };
  }

  private handleDataChannelMessage(data: string): void {
    try {
      const event = JSON.parse(data);
      const type = event.type ?? 'unknown';
      this.emitDebug('event', `Server event: ${type}`, event);

      const textDelta = event.delta ?? event.text ?? event.response?.text?.delta;
      if (type === 'response.text.delta' && typeof textDelta === 'string') {
        this.emitDebug('text', textDelta, event);
      }
      if (type === 'response.audio_transcript.delta' && typeof event.delta === 'string') {
        this.emitDebug('text', event.delta, event);
      }
      if (type === 'response.text.done' || type === 'response.audio_transcript.done') {
        this.emitDebug('text', '[AI text completed]', event);
      }

      const transcript = event.transcript ?? event.item?.content?.[0]?.transcript;
      if (type.includes('input_audio') || type.includes('transcription')) {
        this.emitDebug('transcript', transcript ? `Student transcript: ${transcript}` : `Transcript event: ${type}`, event);
      }
    } catch (error) {
      console.warn('Unrecognized realtime event payload:', data, error);
      this.emitDebug('event', `Non-JSON server event: ${String(data)}`);
    }
  }

  private sendEvent(payload: unknown): void {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      throw new Error(`DataChannel is not open. Current state: ${this.getDataChannelState()}`);
    }
    this.dataChannel.send(JSON.stringify(payload));
    const eventType = typeof payload === 'object' && payload && 'type' in payload ? String(payload.type) : 'unknown';
    this.emitDebug('datachannel', `Sent event: ${eventType}`, payload);
  }

  private waitForDataChannelOpen(): Promise<void> {
    if (!this.dataChannel) {
      return Promise.reject(new Error('DataChannel was not created.'));
    }
    if (this.dataChannel.readyState === 'open') {
      return Promise.resolve();
    }
    return new Promise((resolve, reject) => {
      const timeoutId = window.setTimeout(() => reject(new Error('Timed out waiting for DataChannel to open.')), 15000);
      this.dataChannel!.addEventListener('open', () => {
        window.clearTimeout(timeoutId);
        resolve();
      }, { once: true });
      this.dataChannel!.addEventListener('error', () => {
        window.clearTimeout(timeoutId);
        reject(new Error('DataChannel failed to open.'));
      }, { once: true });
    });
  }

  private waitForIceGatheringComplete(): Promise<void> {
    if (!this.peerConnection || this.peerConnection.iceGatheringState === 'complete') {
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      const timeoutId = window.setTimeout(resolve, 2500);
      this.peerConnection!.addEventListener('icegatheringstatechange', () => {
        if (this.peerConnection?.iceGatheringState === 'complete') {
          window.clearTimeout(timeoutId);
          resolve();
        }
      });
    });
  }

  private emitStatus(status: ConnectionStatus): void {
    this.options?.onStatusChange?.(status);
  }

  private emitDebug(kind: RealtimeDebugEvent['kind'], message: string, raw?: unknown): void {
    if (raw && kind === 'event') {
      console.debug('Realtime event:', raw);
    }
    this.options?.onDebugEvent?.({ kind, message, raw });
  }
}
