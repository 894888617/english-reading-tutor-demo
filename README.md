# AI 英语阅读导师演示

H5 实时语音中文讲解式英语阅读练习 Demo：浏览器展示英文绘本句子与中文释义，点击 **开始陪练** 后通过浏览器麦克风采集 16 kHz、16 bit、单声道 PCM 音频，经 Java Spring Boot 后端 WebSocket 代理转发到阿里云百炼 Qwen3.5-Omni-Plus-Realtime。AI 默认使用中文讲解、鼓励和纠错，英文只用于朗读、带读、单词示范和简单练习。

当前版本使用 **WebSocket 代理模式**，不使用 WebRTC，不创建 Offer SDP，不做 SDP 交换，也不需要 `ALIYUN_WEBRTC_ENDPOINT`。

## 架构

```text
H5 React 前端
  ↓ WebSocket: ws://localhost:8080/ws/realtime
Java Spring Boot 后端代理
  ↓ WebSocket + Authorization: Bearer ${DASHSCOPE_API_KEY}
阿里云百炼 Qwen3.5-Omni-Plus-Realtime
```

- 前端只连接 Java 后端，不直连阿里云，避免暴露 `DASHSCOPE_API_KEY`。
- Java 后端负责连接阿里云 Realtime WebSocket，发送 `session.update`，并转发音频、文本与调试事件。
- 保留 `GET /api/story` 和 `POST /api/logs`。
- 废弃并移除 `POST /api/realtime/sdp`。

## 技术栈

### 前端

- React
- Vite
- TypeScript
- WebSocket
- Web Audio API
- ScriptProcessorNode（Demo 版本；后续可升级 AudioWorklet）

### 后端

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring WebSocket
- Java 原生 `java.net.http.WebSocket`
- Maven

### AI 模型

- 阿里云百炼 Qwen3.5-Omni-Plus-Realtime
- 默认模型：`qwen3.5-omni-plus-realtime`
- 默认声音：`Tina`
- 接入方式：WebSocket

## 项目目录结构

```text
english-reading-tutor-demo/
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.ts
│  ├─ index.html
│  └─ src/
│     ├─ main.tsx
│     ├─ App.tsx
│     ├─ api.ts
│     ├─ realtime-ws.ts
│     ├─ story.ts
│     └─ styles.css
├─ backend-java/
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/demo/readingtutor/
│     │  ├─ ReadingTutorApplication.java
│     │  ├─ config/RealtimeProperties.java
│     │  ├─ config/WebSocketConfig.java
│     │  ├─ controller/StoryController.java
│     │  ├─ controller/LogController.java
│     │  ├─ service/DashScopeRealtimeSession.java
│     │  ├─ ws/RealtimeWebSocketHandler.java
│     │  └─ dto/
│     │     ├─ StoryResponse.java
│     │     ├─ StoryPage.java
│     │     ├─ StorySentence.java
│     │     └─ LogRequest.java
│     └─ resources/application.yml
└─ README.md
```

## 环境变量说明

后端启动前需要配置：

```bash
export DASHSCOPE_API_KEY=你的阿里云百炼APIKey
export DASHSCOPE_REALTIME_WS_URL=wss://dashscope.aliyuncs.com/api-ws/v1/realtime
export REALTIME_MODEL=qwen3.5-omni-plus-realtime
export REALTIME_VOICE=Tina
```

说明：

- `DASHSCOPE_API_KEY`：阿里云百炼 API Key，只在 Java 后端使用，前端不会拿到该密钥。
- `DASHSCOPE_REALTIME_WS_URL`：阿里云 Realtime WebSocket 地址。
  - 北京地址：`wss://dashscope.aliyuncs.com/api-ws/v1/realtime`
  - 新加坡地址：`wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime`
- `REALTIME_MODEL`：默认 `qwen3.5-omni-plus-realtime`。
- `REALTIME_VOICE`：默认 `Tina`。
- 不需要也不再读取 `ALIYUN_WEBRTC_ENDPOINT`。

`backend-java/src/main/resources/application.yml` 默认配置：

```yaml
server:
  port: 8080

aliyun:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY:}
    realtime-ws-url: ${DASHSCOPE_REALTIME_WS_URL:wss://dashscope.aliyuncs.com/api-ws/v1/realtime}
    realtime-model: ${REALTIME_MODEL:qwen3.5-omni-plus-realtime}
    voice: ${REALTIME_VOICE:Tina}
```

## 后端启动方式

```bash
cd backend-java
mvn spring-boot:run
```

后端默认监听：

```text
http://localhost:8080
```

关键接口：

- `GET /api/story`：返回固定绘本内容。
- `POST /api/logs`：Demo 日志接口，仅打印到控制台。
- `WebSocket /ws/realtime`：浏览器实时语音会话入口。

## 前端启动方式

```bash
cd frontend
npm install
npm run dev
```

浏览器访问：

```text
http://localhost:5173
```

前端通过 Vite Proxy 将 `/api` 请求转发到 `http://localhost:8080`。实时语音 WebSocket 默认连接 `ws://localhost:8080/ws/realtime`。

## 前后端 WebSocket 协议

前端发送 JSON 控制消息。`start_lesson` 和 `update_sentence` 会携带当前上传绘本的标题、等级、页码、英文句子、中文释义和重点词，Java 后端据此动态生成中文讲解式 AI Prompt。

```json
{
  "type": "start_lesson",
  "book": {
    "id": "book_001",
    "title": "小兔子",
    "englishTitle": "The Little Rabbit",
    "level": "初学者"
  },
  "pageNo": 1,
  "currentSentence": {
    "english": "The little rabbit is looking for his red hat.",
    "chinese": "小兔子正在找他的红帽子。",
    "keywords": [
      { "word": "rabbit", "meaning": "小兔子" },
      { "word": "red hat", "meaning": "红帽子" }
    ]
  }
}
```

```json
{
  "type": "update_sentence",
  "book": {
    "id": "book_001",
    "title": "小兔子",
    "englishTitle": "The Little Rabbit",
    "level": "初学者"
  },
  "pageNo": 1,
  "currentSentence": {
    "english": "He asks the bird, have you seen my hat?",
    "chinese": "他问鸟儿：你见过我的帽子吗？",
    "keywords": [
      { "word": "bird", "meaning": "鸟儿" },
      { "word": "hat", "meaning": "帽子" }
    ]
  }
}
```

```json
{
  "type": "repeat_sentence",
  "currentSentence": {
    "english": "The little rabbit is looking for his red hat.",
    "chinese": "小兔子正在找他的红帽子。"
  }
}
```

```json
{ "type": "stop" }
```

前端发送 Binary 消息：

- 16 kHz、16 bit、单声道 PCM。
- 每个 chunk 约 100 ms。
- Java 后端 base64 编码后以 `input_audio_buffer.append` 转发给阿里云。

后端发送 JSON 消息：

- `{ "type": "status", "message": "已连接到百炼实时语音模型。" }`
- `{ "type": "dashscope_event", "event": { } }`
- `{ "type": "ai_text_delta", "text": "..." }`
- `{ "type": "ai_text_done", "text": "..." }`
- `{ "type": "error", "message": "具体错误" }`

后端发送 Binary 消息：

- 阿里云 `response.audio.delta` 解码后的 PCM bytes。
- 前端通过 Web Audio API 排队播放，避免音频块重叠。

## 本地测试步骤

1. 准备阿里云百炼 API Key，并确认该 Key 有权访问实时 WebSocket 模型。
2. 设置环境变量：

   ```bash
   export DASHSCOPE_API_KEY=你的阿里云百炼APIKey
   export DASHSCOPE_REALTIME_WS_URL=wss://dashscope.aliyuncs.com/api-ws/v1/realtime
   export REALTIME_MODEL=qwen3.5-omni-plus-realtime
   export REALTIME_VOICE=Tina
   ```

3. 启动后端：

   ```bash
   cd backend-java
   mvn spring-boot:run
   ```

4. 验证绘本接口：

   ```bash
   curl http://localhost:8080/api/story
   ```

5. 启动前端：

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

6. 打开浏览器访问 `http://localhost:5173`。
7. 确认页面展示绘本标题、等级、当前页、当前句子和调试区。
8. 点击“开始陪练”，允许浏览器麦克风权限。
9. 观察调试区：应看到浏览器 WebSocket 连接、后端连接阿里云、`session.update`、音频 chunk 发送、阿里云事件和 AI 文本增量。
10. 听 AI 老师先用中文说明当前句子，再朗读英文、解释中文意思、拆解重点词并带读。
11. 点击“重复朗读”，AI 应重新朗读英文句子，并用中文提醒学生跟读。
12. 点击“下一句”，页面高亮更新，前端发送 `update_sentence`，Java 后端发送新的中文教学 `session.update`，AI 围绕新句子继续。
13. 点击“结束会话”，确认麦克风释放、播放队列清理、浏览器 WebSocket 和阿里云 WebSocket 关闭。

## 常见问题

### 1. WebSocket 连接失败应该检查什么？

- 后端是否运行在 `http://localhost:8080`。
- `DASHSCOPE_API_KEY` 是否已设置，且没有过期。
- `DASHSCOPE_REALTIME_WS_URL` 是否为可用区域地址。
- `REALTIME_MODEL` 是否为 `qwen3.5-omni-plus-realtime` 或你的账号可用的实时模型。
- API Key 是否有百炼实时语音 WebSocket 访问权限。
- 浏览器是否允许麦克风权限。
- 页面是否通过 `http://localhost` 或 HTTPS 打开；非安全上下文可能无法调用麦克风。
- 公司代理、防火墙或本机网络是否阻断 WebSocket 请求。

### 2. 页面加载不到绘本怎么办？

- 确认后端运行在 `http://localhost:8080`。
- 直接访问 `http://localhost:8080/api/story` 检查接口。
- 如果前端和后端不在同一机器，设置 `VITE_API_BASE_URL` 为后端地址。

### 3. 为什么后端不把 API Key 返回给前端？

API Key 属于服务端密钥。前端只发送控制消息和 PCM 音频给 Java 后端，由后端添加 `Authorization: Bearer ${DASHSCOPE_API_KEY}` 后连接阿里云，避免密钥泄露。

### 4. AI 回答跑题怎么办？

开始课程和切换句子时，Java 后端都会发送中文教学 `session.update`，其中 `instructions` 明确要求 AI 主要使用中文，按“中文说明当前句子 → 朗读英文 → 中文解释 → 讲重点词 → 带读 → 问一个简单问题”的流程教学，只围绕当前绘本和当前句子讲解、提问、纠错，不做开放闲聊。

## 绘本上传说明

当前 Demo 支持：

- 普通 PDF 文本提取：后端使用 Apache PDFBox 按页提取文本，并按 `. ? !` 自动切分英文句子。
- JPG / PNG 上传：Demo 阶段会创建绘本页面，并通过 OCR 预留接口提示用户手动填写英文文本。
- 扫描版 PDF 和图片 OCR 预留：默认 `ocr.provider=mock`，后续可替换为百度 OCR / 腾讯 OCR / 阿里 OCR。
- 上传后编辑：前端支持修改标题、等级、页面原始文本、英文句子、中文意思和重点词，也可以新增或删除句子。

注意：

- 如果 PDF 是扫描版，可能无法自动提取文本。
- 如果 OCR 服务未配置，需要手动填写英文句子。
- 上传后建议先校对英文句子，再开始 AI 学习。
- Demo 阶段使用本地文件和 JSON 元数据存储，不使用数据库。

本地存储目录：

```text
backend-java/
├─ data/
│  ├─ uploads/   # 原始上传文件
│  ├─ books/     # book-{bookId}.json
│  └─ covers/    # 封面图片预留目录
```

上传限制默认配置：

```yaml
app:
  upload:
    max-file-size-mb: 20
    max-pdf-pages: 30

ocr:
  provider: mock
  baidu:
    api-key:
    secret-key:
  tencent:
    secret-id:
    secret-key:
```

新增绘本接口：

- `POST /api/books/upload`：上传并解析 PDF / JPG / PNG。
- `GET /api/books`：获取绘本列表；没有上传绘本时返回默认测试绘本。
- `GET /api/books/{bookId}`：获取完整绘本详情。
- `PUT /api/books/{bookId}`：保存校对后的绘本内容。
- `DELETE /api/books/{bookId}`：删除绘本 JSON 和原始上传文件。
- `GET /api/story`：保留兼容旧 Demo，返回默认测试绘本。

推荐学习流程：

```text
1. 打开页面
2. 点击“上传绘本”
3. 选择 PDF / JPG / PNG
4. 系统解析
5. 页面展示解析出的页和句子
6. 用户校对英文句子、中文意思、关键词
7. 点击“保存绘本”
8. 点击“开始学习这本绘本”
9. AI 按该绘本当前句子进行中文讲解和英文带读
10. 用户跟读
11. AI 指出问题和改进建议
12. 进入下一句
```
