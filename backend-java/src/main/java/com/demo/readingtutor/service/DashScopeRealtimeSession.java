package com.demo.readingtutor.service;

import com.demo.readingtutor.config.RealtimeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class DashScopeRealtimeSession implements WebSocket.Listener {
    private static final Logger log = LoggerFactory.getLogger(DashScopeRealtimeSession.class);
    private static final String INTRO_PROMPT = "请开始这节英语阅读课。请用中文讲解当前英文句子，先朗读英文句子，再解释中文意思，然后带我跟读。";

    private final RealtimeProperties properties;
    private final ObjectMapper objectMapper;
    private final Consumer<String> jsonToBrowser;
    private final Consumer<byte[]> audioToBrowser;
    private final HttpClient httpClient;
    private final StringBuilder textBuffer = new StringBuilder();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingOutboundMessages = new ConcurrentLinkedQueue<>();
    private volatile WebSocket webSocket;
    private volatile String storyTitle = "";
    private volatile String englishTitle = "";
    private volatile String currentSentenceEnglish = "";
    private volatile String currentSentenceChinese = "";

    public DashScopeRealtimeSession(
            RealtimeProperties properties,
            ObjectMapper objectMapper,
            Consumer<String> jsonToBrowser,
            Consumer<byte[]> audioToBrowser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.jsonToBrowser = jsonToBrowser;
        this.audioToBrowser = audioToBrowser;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public void connect() {
        if (!properties.hasApiKey()) {
            sendBrowserError("未配置 DASHSCOPE_API_KEY，请先设置后端环境变量。");
            return;
        }
        URI uri = UriComponentsBuilder.fromUriString(properties.getRealtimeWsUrl())
                .queryParam("model", properties.getRealtimeModel())
                .build(true)
                .toUri();

        httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + properties.getApiKey())
                .connectTimeout(Duration.ofSeconds(15))
                .buildAsync(uri, this)
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        log.warn("Failed to connect to DashScope realtime WebSocket", error);
                        sendBrowserError("连接百炼实时语音模型失败：" + error.getMessage());
                    } else {
                        this.webSocket = socket;
                    }
                });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        open.set(true);
        sendBrowserJson(Map.of("type", "status", "message", "已连接到百炼实时语音模型。"));
        sendSessionUpdate();
        flushPendingMessages();
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        textBuffer.append(data);
        if (last) {
            String message = textBuffer.toString();
            textBuffer.setLength(0);
            handleDashScopeMessage(message);
        }
        webSocket.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        open.set(false);
        sendBrowserJson(Map.of("type", "status", "message", "百炼实时语音连接已关闭。", "code", statusCode, "reason", reason));
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        log.warn("DashScope realtime WebSocket error", error);
        sendBrowserError("百炼实时语音 WebSocket 错误：" + error.getMessage());
    }

    public void startLesson(String storyTitle, String englishTitle, String currentSentenceEnglish, String currentSentenceChinese) {
        this.storyTitle = storyTitle == null ? "" : storyTitle;
        this.englishTitle = englishTitle == null ? "" : englishTitle;
        this.currentSentenceEnglish = currentSentenceEnglish == null ? "" : currentSentenceEnglish;
        this.currentSentenceChinese = currentSentenceChinese == null ? "" : currentSentenceChinese;
        sendSessionUpdate();
        sendTextInstruction(INTRO_PROMPT);
    }

    public void updateSentence(String storyTitle, String englishTitle, String currentSentenceEnglish, String currentSentenceChinese) {
        this.storyTitle = storyTitle == null ? this.storyTitle : storyTitle;
        this.englishTitle = englishTitle == null ? this.englishTitle : englishTitle;
        this.currentSentenceEnglish = currentSentenceEnglish == null ? "" : currentSentenceEnglish;
        this.currentSentenceChinese = currentSentenceChinese == null ? "" : currentSentenceChinese;
        sendSessionUpdate();
        sendTextInstruction("我们进入下一句。请先听我读一遍，再用中文解释意思，并带我跟读：" + this.currentSentenceEnglish);
    }

    public void repeatSentence(String currentSentenceEnglish, String currentSentenceChinese) {
        String sentence = StringUtils.hasText(currentSentenceEnglish) ? currentSentenceEnglish : this.currentSentenceEnglish;
        if (StringUtils.hasText(currentSentenceChinese)) {
            this.currentSentenceChinese = currentSentenceChinese;
        }
        sendTextInstruction("请重新朗读这个英文句子，并用中文提醒我跟读：" + sentence);
    }

    public void sendAudio(byte[] pcmBytes) {
        if (pcmBytes == null || pcmBytes.length == 0 || !isOpen()) {
            return;
        }
        String audio = Base64.getEncoder().encodeToString(pcmBytes);
        sendDashScopeJson(Map.of("type", "input_audio_buffer.append", "audio", audio));
    }

    public void sendTextInstruction(String text) {
        if (!StringUtils.hasText(text)) {
            return;
        }
        sendDashScopeJson(Map.of(
                "type", "conversation.item.create",
                "item", Map.of(
                        "type", "message",
                        "role", "user",
                        "content", List.of(Map.of("type", "input_text", "text", text))
                )
        ));
        sendDashScopeJson(Map.of("type", "response.create"));
    }

    public void close() {
        open.set(false);
        WebSocket socket = webSocket;
        if (socket != null) {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "browser session closed");
        }
    }

    private boolean isOpen() {
        return open.get() && webSocket != null;
    }

    private void sendSessionUpdate() {
        sendDashScopeJson(Map.of(
                "type", "session.update",
                "session", Map.of(
                        "modalities", List.of("text", "audio"),
                        "voice", properties.getVoice(),
                        "input_audio_format", "pcm",
                        "output_audio_format", "pcm",
                        "instructions", buildTutorInstructions(),
                        "turn_detection", Map.of(
                                "type", "server_vad",
                                "threshold", 0.5,
                                "silence_duration_ms", 800
                        ),
                        "temperature", 0.6
                )
        ));
    }

    private String buildTutorInstructions() {
        return buildTutorInstructions(
                storyTitle,
                englishTitle,
                currentSentenceEnglish,
                currentSentenceChinese
        );
    }

    private String buildTutorInstructions(
            String storyTitle,
            String englishTitle,
            String currentSentenceEnglish,
            String currentSentenceChinese
    ) {
        return "你是一名中文授课的少儿英语阅读老师，正在带 6-10 岁中国孩子阅读英文绘本。\n\n"
                + "你的教学语言：\n"
                + "- 默认使用中文讲解。\n"
                + "- 英文只用于朗读、跟读示范、单词示范和简单练习。\n"
                + "- 不要长时间全英文输出。\n"
                + "- 不要开放闲聊。\n"
                + "- 不要讲复杂语法。\n\n"
                + "当前故事中文名：\n" + safe(storyTitle) + "\n\n"
                + "当前故事英文名：\n" + safe(englishTitle) + "\n\n"
                + "当前英文句子：\n" + safe(currentSentenceEnglish) + "\n\n"
                + "当前中文意思：\n" + safe(currentSentenceChinese) + "\n\n"
                + "你的任务：\n"
                + "1. 用中文告诉学生现在学习哪一句。\n"
                + "2. 清晰朗读英文句子。\n"
                + "3. 用中文解释这句话的意思。\n"
                + "4. 挑出 1-2 个重点英文单词，用中文解释。\n"
                + "5. 带学生跟读英文句子。\n"
                + "6. 问一个非常简单的理解问题。\n"
                + "7. 学生回答错误时，先鼓励，再用中文纠正。\n"
                + "8. 学生读音不清楚时，温和提醒，并给出正确示范。\n"
                + "9. 每次回答要短，不要一次讲太多。\n"
                + "10. 一次只问一个问题。\n\n"
                + "回答风格：\n"
                + "- 像耐心的中文英语老师。\n"
                + "- 语言简单。\n"
                + "- 语气鼓励。\n"
                + "- 适合小学生。\n"
                + "- 不要说太长。\n"
                + "- 不要输出与当前绘本无关的内容。\n\n"
                + "推荐教学话术：\n"
                + "- “我们先来看这一句……”\n"
                + "- “这句话的意思是……”\n"
                + "- “这个单词的意思是……”\n"
                + "- “请跟我读……”\n"
                + "- “很好，再来一遍。”\n"
                + "- “没关系，我们慢慢来。”\n"
                + "- “我问你一个小问题……”\n"
                + "- “你可以这样回答……”\n\n"
                + "限制：\n"
                + "- 不要变成纯聊天。\n"
                + "- 不要讲和绘本无关的内容。\n"
                + "- 不要一次提多个问题。\n"
                + "- 不要输出大段英文解释。\n"
                + "- 不要让孩子感到被批评。";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void handleDashScopeMessage(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String type = event.path("type").asText("unknown");
            if ("response.audio.delta".equals(type)) {
                String audio = event.path("audio").asText(event.path("delta").asText(""));
                if (StringUtils.hasText(audio)) {
                    audioToBrowser.accept(Base64.getDecoder().decode(audio));
                }
                return;
            }
            if ("response.audio_transcript.delta".equals(type)) {
                sendBrowserJson(Map.of("type", "ai_text_delta", "text", event.path("delta").asText("")));
                return;
            }
            if ("response.audio_transcript.done".equals(type)) {
                sendBrowserJson(Map.of("type", "ai_text_done", "text", event.path("transcript").asText(event.path("text").asText(""))));
                return;
            }
            if ("error".equals(type) || event.has("error")) {
                log.warn("DashScope realtime returned error event: {}", message);
                sendBrowserJson(Map.of("type", "error", "message", message));
                return;
            }
            sendBrowserJson(Map.of("type", "dashscope_event", "event", event));
        } catch (Exception ex) {
            log.warn("Failed to parse DashScope realtime message: {}", message, ex);
            sendBrowserError("解析百炼消息失败：" + ex.getMessage());
        }
    }

    private void sendDashScopeJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (!isOpen()) {
                pendingOutboundMessages.add(json);
                return;
            }
            webSocket.sendText(json, true);
        } catch (JsonProcessingException ex) {
            sendBrowserError("序列化百炼事件失败：" + ex.getMessage());
        }
    }

    private void flushPendingMessages() {
        if (!isOpen()) {
            return;
        }
        String json;
        while ((json = pendingOutboundMessages.poll()) != null) {
            webSocket.sendText(json, true);
        }
    }

    private void sendBrowserJson(Object payload) {
        try {
            jsonToBrowser.accept(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize browser event", ex);
        }
    }

    private void sendBrowserError(String message) {
        sendBrowserJson(Map.of("type", "error", "message", message));
    }
}
