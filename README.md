# English Reading Tutor Demo

一个 H5 实时语音英语外教 Demo：浏览器展示英文绘本句子，点击“Start Tutor”后请求麦克风权限，通过 WebRTC 创建 Offer SDP，经 Java Spring Boot 后端转发到阿里云百炼 Qwen3.5-Omni-Plus-Realtime，返回 Answer SDP 后建立实时语音对话。

Demo 聚焦英语阅读陪练闭环：标准朗读、单词解释、句子解释、阅读理解提问、学生回答纠错、鼓励式反馈，以及必要时的简短中文解释。不包含账号、数据库、OCR、老师端、家长端、支付或正式权限系统。

## 技术栈

### 前端

- React
- Vite
- TypeScript
- WebRTC / DataChannel
- 普通 CSS

### 后端

- Java 17
- Spring Boot 3.x
- Spring Web
- Spring Validation
- RestClient
- Maven

### AI 模型

- 阿里云百炼 Qwen3.5-Omni-Plus-Realtime
- 模型参数：`qwen3.5-omni-plus-realtime`
- 接入方式：WebRTC SDP 交换
- 后端保护 `DASHSCOPE_API_KEY`，前端不会暴露 API Key

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
│     ├─ realtime.ts
│     ├─ story.ts
│     └─ styles.css
├─ backend-java/
│  ├─ pom.xml
│  └─ src/main/
│     ├─ java/com/demo/readingtutor/
│     │  ├─ ReadingTutorApplication.java
│     │  ├─ config/RealtimeProperties.java
│     │  ├─ controller/StoryController.java
│     │  ├─ controller/RealtimeController.java
│     │  ├─ controller/LogController.java
│     │  ├─ service/RealtimeService.java
│     │  └─ dto/
│     │     ├─ StoryResponse.java
│     │     ├─ StoryPage.java
│     │     └─ LogRequest.java
│     └─ resources/application.yml
└─ README.md
```

## 环境变量说明

后端启动前需要配置：

```bash
export DASHSCOPE_API_KEY=你的阿里云百炼APIKey
export ALIYUN_WEBRTC_ENDPOINT=你的WebRTC Endpoint，不要带 https://
export REALTIME_MODEL=qwen3.5-omni-plus-realtime
export REALTIME_VOICE=Ethan
```

说明：

- `DASHSCOPE_API_KEY`：阿里云百炼 API Key，只在 Java 后端使用。
- `ALIYUN_WEBRTC_ENDPOINT`：WebRTC Endpoint Host，不要带 `https://`，后端会拼接为 `https://${ALIYUN_WEBRTC_ENDPOINT}/api/v1/webrtc/realtime?model=${REALTIME_MODEL}`。
- `REALTIME_MODEL`：默认 `qwen3.5-omni-plus-realtime`。
- `REALTIME_VOICE`：默认 `Ethan`。

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
- `POST /api/realtime/sdp`：接收 `application/sdp` Offer SDP，转发给阿里云百炼，返回 Answer SDP。
- `POST /api/logs`：Demo 日志接口，仅打印到控制台。

## 前端启动方式

```bash
cd frontend
npm install
npm run dev
```

访问地址：

```text
http://localhost:5173
```

前端通过 Vite Proxy 将 `/api` 请求转发到 `http://localhost:8080`，也可以通过 `VITE_API_BASE_URL` 指定后端地址。

## 本地测试步骤

1. 准备阿里云百炼 API Key，并确认该 Key 有权访问实时 WebRTC 模型。
2. 设置环境变量：

   ```bash
   export DASHSCOPE_API_KEY=你的阿里云百炼APIKey
   export ALIYUN_WEBRTC_ENDPOINT=你的WebRTC Endpoint，不要带 https://
   export REALTIME_MODEL=qwen3.5-omni-plus-realtime
   export REALTIME_VOICE=Ethan
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
8. 点击 `Start Tutor`，允许浏览器麦克风权限。
9. 观察调试区：应看到 Offer SDP 创建、后端 SDP 交换、Answer SDP 返回、DataChannel 打开、`session.update` 发送等事件。
10. 听 AI 外教朗读当前句子并提问；用英语语音回答或提问。
11. 点击 `Repeat Reading`，AI 应重新慢速清晰朗读当前句子。
12. 点击 `Next Sentence`，页面高亮更新，前端重新发送 `session.update`，AI 围绕新句子朗读和提问。
13. 点击 `Stop Session`，确认麦克风释放、DataChannel 和 PeerConnection 关闭。

## 常见问题

### 1. WebRTC 连接失败应该检查什么？

- `DASHSCOPE_API_KEY` 是否已设置，且没有过期。
- `ALIYUN_WEBRTC_ENDPOINT` 是否只填写 Host，未包含 `https://`，且没有多余路径。
- `REALTIME_MODEL` 是否为 `qwen3.5-omni-plus-realtime` 或你的账号可用的实时模型。
- API Key 是否有百炼实时语音 / WebRTC 访问权限。
- 后端日志中 SDP 长度是否正常，阿里云响应状态码是否为成功状态。
- 浏览器是否允许麦克风权限。
- 页面是否通过 `http://localhost` 或 HTTPS 打开；非安全上下文可能无法调用麦克风。
- 公司代理、防火墙或本机网络是否阻断 WebRTC / HTTPS 请求。
- 前端请求 `/api/realtime/sdp` 是否成功到达 Java 后端。

### 2. 页面加载不到绘本怎么办？

- 确认后端运行在 `http://localhost:8080`。
- 直接访问 `http://localhost:8080/api/story` 检查接口。
- 如果前端和后端不在同一机器，设置 `VITE_API_BASE_URL` 为后端地址。

### 3. 为什么后端不把 API Key 返回给前端？

API Key 属于服务端密钥，前端只发送 Offer SDP 给 Java 后端，由后端添加 `Authorization: Bearer ${DASHSCOPE_API_KEY}` 后转发到阿里云，避免密钥泄露。

### 4. AI 回答跑题怎么办？

前端每次建立连接和切换句子都会通过 DataChannel 发送 `session.update`，其中 `instructions` 明确要求 AI 只围绕当前绘本和当前句子讲解、提问、纠错，不做开放闲聊。
