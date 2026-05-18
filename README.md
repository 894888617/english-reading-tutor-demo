# AI 英语阅读陪练小程序

本项目已将 OCR、绘本朗读、跟读评分、发音纠错、短语音反馈、实时翻译、实时外教对话拆分为独立 Provider。生产默认不启用模拟数据；缺少服务密钥时返回明确错误，不再静默回退到固定文本或随机评分。

## 模型与 Provider 拆分

| 能力 | Provider / 模型 | 说明 |
| --- | --- | --- |
| OCR | `OCR_PROVIDER=dashscope`，`OCR_MODEL=qwen-vl-ocr-2025-11-20` | 图片与扫描 PDF 页面调用 DashScope Qwen-OCR；普通 PDF 优先提取内嵌文本。 |
| 绘本朗读 | `TTS_PROVIDER=dashscope`，`TTS_MODEL=qwen3-tts-flash` | 固定文本走非实时 TTS，并写入本地音频缓存。 |
| 跟读评分 | `SPEECH_EVAL_PROVIDER=iflytek` | 讯飞语音评测 `wss://` endpoint 通过 WebSocket 鉴权与传输；未配置账号时直接报“语音评测服务未配置”。 |
| 发音纠错 | 语音评测返回的 word-level 结果 | 前端按 word status 标红错词，点击错词走 TTS 标准发音。 |
| 短语音反馈 | 规则短反馈 + TTS | 跟读完成后生成儿童友好短句，并通过 TTS 缓存播放。 |
| 实时翻译 | `qwen3-livetranslate-flash-realtime` | 独立 session 接口，不用于跟读评分。 |
| 实时外教对话 | `qwen3.5-omni-plus-realtime` | 保留实时外教；前端检测插话后清空播放队列并发送 `response.cancel`。 |

后端新增 AI 服务层：

```text
backend-java/src/main/java/com/demo/readingtutor/ai/
  AiServices.java
  ModelRegistry.java
  cache/AudioCacheService.java
  controller/{TtsController,FeedbackController,RealtimeAiController}.java
  providers/{OcrProvider,TtsProvider,SpeechEvalProvider,DashScopeOcrProvider,DashScopeTtsProvider,IflytekSpeechEvalProvider,DashScopeRealtimeProvider}.java
  types/AiTypes.java
```

## 环境变量

复制并编辑：

```bash
cp .env.example .env
```

关键变量：

```bash
NODE_ENV=production
ENABLE_AI_MOCK=false

DASHSCOPE_API_KEY=
DASHSCOPE_REGION=beijing
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/api/v1
DASHSCOPE_REALTIME_WS_URL=wss://dashscope.aliyuncs.com/api-ws/v1/realtime

OCR_PROVIDER=dashscope
OCR_MODEL=qwen-vl-ocr-2025-11-20

TTS_PROVIDER=dashscope
TTS_MODEL=qwen3-tts-flash
TTS_VOICE=Cherry
TTS_LANGUAGE=English
TTS_FORMAT=mp3
TTS_SPEED=0.9
TTS_PITCH=1.0
TTS_VOLUME=1.0

SPEECH_EVAL_PROVIDER=iflytek
IFLYTEK_APP_ID=
IFLYTEK_API_KEY=
IFLYTEK_API_SECRET=
IFLYTEK_EVAL_ENDPOINT=

LIVE_TRANSLATE_MODEL=qwen3-livetranslate-flash-realtime
REALTIME_TUTOR_MODEL=qwen3.5-omni-plus-realtime
REALTIME_TUTOR_PROTOCOL=webrtc
REALTIME_TUTOR_VAD_ENABLED=true
REALTIME_TUTOR_INTERRUPT_ENABLED=true

AUDIO_CACHE_DRIVER=local
AUDIO_CACHE_DIR=./storage/audio-cache
AUDIO_CACHE_TTL_DAYS=365

# 前端开发环境访问 Java 后端静态音频与 API
VITE_API_BASE_URL=http://localhost:8080
```

> 本地开发如确需保留模拟评测/OCR，必须同时显式设置 `ENABLE_AI_MOCK=true` 与相应 mock provider；生产环境保持 `ENABLE_AI_MOCK=false`。

## API 接口

### OCR / 绘本上传

- `POST /api/books/upload`
  - `multipart/form-data`: `file`, `title`, `englishTitle`, `level`
  - 图片：上传后立即 OCR，OCR 文本写入 `BookPage.rawText` 并自动切句。
  - PDF：普通 PDF 优先提取内嵌文本；空文本页渲染成图片后调用 OCR。
  - OCR 失败页返回 `needOcr=true` 与 `parseError`，老师后台可手动修正并保存。
- `PUT /api/books/{bookId}`：保存老师人工修正后的 OCR 文本、句子、中文释义与重点词。

### TTS 与声音控制

- `GET /api/tts/voices`：返回当前模型允许使用的声音列表。
- `POST /api/tts/synthesize`

```json
{
  "text": "The little rabbit is looking for his red hat.",
  "language": "English",
  "voice": "Serena",
  "speed": 0.75,
  "pitch": 1.0,
  "volume": 1.0,
  "bookId": "book_xxx",
  "pageId": "1",
  "sentenceId": "0"
}
```

返回包含 `audioUrl`、`cacheHit`、`durationMs`、`provider`、`model`、`voice`、`language`、`speed`、`pitch`、`volume`、`format`。缓存 key 包含 `textHash + model + voice + speed + pitch + volume + language + format`，切换音色或语速不会命中旧音频；`audioUrl` 始终是 `/audio-cache/*.mp3` 这类浏览器可访问 URL，不返回服务器文件路径。

- `POST /api/tts/cache/clear`：清空本地音频缓存。

#### 音频缓存静态资源与 nginx

后端将 `AUDIO_CACHE_DIR` 暴露为 `GET /audio-cache/**`。本地 Vite 开发时请设置 `frontend/.env.development`：

```env
VITE_API_BASE_URL=http://localhost:8080
```

生产如果 nginx 已经同域代理 `/api` 和 `/audio-cache`，`VITE_API_BASE_URL` 可以留空；如果前后端不同域，请设置为后端域名。nginx 同域部署时需要代理音频缓存：

```nginx
location /audio-cache/ {
    proxy_pass http://127.0.0.1:8080/audio-cache/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
```

Docker compose 中如果后端服务名为 `backend`，可使用 `proxy_pass http://backend:8080/audio-cache/;`。浏览器访问 `/audio-cache/*.mp3` 应返回 `200` 与 `Content-Type: audio/mpeg`。


### 跟读评分与发音纠错

- `POST /api/speech/evaluate`
  - `multipart/form-data`: `file`, `referenceText`, `sentenceId`, `bookId`, `pageId`
  - 返回四维评分、word-level 结果与 `evaluationId`。
- `GET /api/speech/evaluations/{id}`：读取已保存评分。

讯飞配置示例：

```env
SPEECH_EVAL_PROVIDER=iflytek
ENABLE_AI_MOCK=false
IFLYTEK_APP_ID=your_app_id
IFLYTEK_API_KEY=your_api_key
IFLYTEK_API_SECRET=your_api_secret
IFLYTEK_EVAL_ENDPOINT=wss://...
```

当 `IFLYTEK_EVAL_ENDPOINT` 以 `wss://` 或 `ws://` 开头时，后端使用 WebSocket 客户端生成 `host/date/request-line` HMAC-SHA256 鉴权 URL，并发送 `common/business/data` 帧；不会用普通 HTTP Client 直接请求 `wss://` 地址。日志不会打印 API Secret。


### 短语音反馈

- `POST /api/feedback/speech`

```json
{ "evaluationId": "eval_xxx", "voice": "Cherry" }
```

返回短反馈文本与 TTS 音频 URL。

### 实时模块

- `POST /api/realtime/live-translate/session`：创建实时翻译会话描述，模型固定为 `qwen3-livetranslate-flash-realtime`。
- `POST /api/realtime/tutor/webrtc-session`：创建实时外教会话描述，模型固定为 `qwen3.5-omni-plus-realtime`。
- `WebSocket /ws/realtime`：现有外教对话代理，支持 VAD 配置、`response.cancel`、用户插话清空播放队列。

## 前端改造

学生阅读页支持：

- 整页朗读、逐句播放、重复本句、慢速播放、单词点读。
- 外教声音选择、TTS 生成语速、pitch、volume、language、浏览器 playbackRate。
- 当前 TTS 模型、实际 voice、是否缓存命中展示。
- 录音上传评分、四维评分展示、错词标红、点击错词播放标准发音。
- 跟读后短语音反馈播放。

老师后台支持：

- 图片 / PDF 上传。
- OCR 结果预览、失败提示、手动修正文本。
- 自动切句结果预览与保存。

## 错误处理

- 缺少 `DASHSCOPE_API_KEY`：返回 “DashScope API Key 未配置”。
- 缺少 OCR Key：返回 “OCR 服务暂未配置，请先配置 API Key”。
- 缺少语音评测账号：返回 “语音评测服务未配置”。
- TTS 生成失败：前端提示 “朗读音频生成失败，请稍后重试”；音频资源加载失败时提示 “朗读音频加载失败，请检查音频地址、格式或后端静态资源配置”，并在浏览器控制台输出 `[TTS result]`、raw/resolved audioUrl 与 media error 详情。
- 语音评分失败：前端提示 “评分失败，请重新录音”。
- 所有后端错误响应包含 `requestId`，日志包含 `requestId`，不会打印 API Key。

## 启动与测试步骤

```bash
cd backend-java
mvn spring-boot:run
```

```bash
cd frontend
npm install
npm run dev
```

验收建议：

1. 配置 `DASHSCOPE_API_KEY`，上传英文绘本图片，确认页面显示 OCR 真实文本。
2. 上传普通 PDF，确认优先提取内嵌文本。
3. 上传扫描 PDF，确认按页 OCR；失败页在老师后台可人工修正。
4. 点击整页朗读、逐句播放、单词点读，确认播放 `/audio-cache/*.mp3`。
5. 切换 `voice/speed/pitch/volume`，确认返回的 `model/voice/cacheHit` 与 UI 一致，且不会播放旧 voice 缓存。
6. 配置专业语音评测账号，提交录音，确认返回四维评分与 word-level 纠错。
7. 实时外教播放时插话，确认前端清空本地播放队列并发送 `response.cancel`。

## 已处理的模拟逻辑清单

- 生产默认 `ENABLE_AI_MOCK=false`。
- OCR 默认 provider 从 mock 改为 DashScope。
- 跟读评分默认 provider 从 mock 改为专业语音评测 provider。
- 缺少 API Key 不再返回固定 OCR 文本或模拟评分，改为明确错误。
- 固定绘本朗读不再走 realtime 对话模型，改为 TTS + 缓存。
- 短语音反馈不再走 realtime 对话模型，改为短文本 + TTS。

## 仍需甲方提供的三方账号

1. DashScope / 阿里云百炼 API Key（OCR、TTS、实时翻译、实时外教）。
2. 生产可用的 TTS 官方音色或自定义 voiceId / 复刻音色 ID。
3. 专业口语评测服务账号（讯飞 / 有道 / 腾讯云 / 阿里语音评测），包括 App ID、API Key、API Secret、评测接口地址。
4. 如切换百度 OCR 或腾讯 OCR，需提供对应密钥。

## Audio recording, speech evaluation, and TTS troubleshooting

### ffmpeg requirement for Web recordings

Browser `MediaRecorder` commonly produces `audio/webm;codecs=opus` (or another browser-native container), not the raw PCM format required by iFlytek speech evaluation. The Java backend therefore accepts browser uploads such as WebM/Opus, MP3, WAV, M4A, OGG, and MP4 and transcodes them with `ffmpeg` before sending audio to iFlytek.

Install `ffmpeg` locally if you run the backend outside Docker:

- Debian/Ubuntu: `sudo apt-get update && sudo apt-get install -y ffmpeg`
- macOS/Homebrew: `brew install ffmpeg`
- Alpine: `apk add --no-cache ffmpeg`

The backend Docker image installs `ffmpeg` automatically. You can override the executable path with `FFMPEG_PATH`.

### Speech evaluation audio format

The backend normalizes uploaded reading recordings to:

- 16,000 Hz sample rate
- mono channel
- signed 16-bit little-endian PCM (`s16le`)
- iFlytek business parameters: `aue=raw`, `auf=audio/L16;rate=16000`

Relevant backend configuration:

```yaml
audio:
  ffmpeg-path: ${FFMPEG_PATH:ffmpeg}
  target-sample-rate: ${AUDIO_TARGET_SAMPLE_RATE:16000}
  target-channels: ${AUDIO_TARGET_CHANNELS:1}
  target-format: ${AUDIO_TARGET_FORMAT:pcm}
  temp-dir: ${AUDIO_TEMP_DIR:./storage/audio-temp}
```

### Web recording behavior

The frontend still records with `MediaRecorder` and uploads the real binary `Blob` as multipart field `file`; it does not upload only a blob URL. The upload also includes the current sentence's English `referenceText`, `bookId`, `pageId`, `sentenceId`, and actual recorder `mimeType`. After recording stops, the page creates a local preview URL so students can play the recording before and after scoring.

### TTS volume troubleshooting

TTS volume uses a 0-1 UI scale. The frontend applies the returned TTS `volume` to `HTMLAudioElement.volume`, ensures `muted=false`, and keeps browser `playbackRate` separate from generated TTS `speed`:

- `speed`: affects generated audio and TTS cache keys.
- `playbackRate`: affects only browser playback.
- `volume`: sent to the backend/provider and applied to browser audio volume.

The `/api/tts/synthesize` response includes the actual provider/model/voice/language/speed/pitch/volume/format/cacheHit values used by the backend. If the UI-selected voice differs from the returned voice, the student page shows a fallback notice.

The TTS cache key includes text hash, model, voice, language, speed, pitch, volume, and format. Use `POST /api/tts/cache/clear` or the student page “清除 TTS 缓存” button to rule out stale cached audio while debugging.

If audio is still too quiet with `audio.volume=1`, open the returned `/audio-cache/*.mp3` URL directly. If it is quiet there too, investigate provider output or volume parameters; if direct playback is normal, inspect frontend audio state.

### VITE_API_BASE_URL

When the frontend and backend run on different origins in development, set:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

The frontend resolves TTS and cached audio with `resolveAssetUrl(result.audioUrl)`. In production, `VITE_API_BASE_URL` may be empty if Nginx proxies `/api` and `/audio-cache` to the backend.
