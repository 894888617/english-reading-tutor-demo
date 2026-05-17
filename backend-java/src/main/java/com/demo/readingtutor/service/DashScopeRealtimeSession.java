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
    private final VoiceMappingService voiceMappingService;
    private final Consumer<String> jsonToBrowser;
    private final Consumer<byte[]> audioToBrowser;
    private final HttpClient httpClient;
    private final StringBuilder textBuffer = new StringBuilder();
    private final AtomicBoolean open = new AtomicBoolean(false);
    private final AtomicBoolean hasActiveResponse = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingOutboundMessages = new ConcurrentLinkedQueue<>();
    private volatile WebSocket webSocket;
    private volatile String bookTitle = "";
    private volatile String englishTitle = "";
    private volatile String level = "";
    private volatile int pageNo = 1;
    private volatile String currentSentenceEnglish = "";
    private volatile String currentSentenceChinese = "";
    private volatile String currentKeywords = "";
    private volatile String voiceStyle = "";
    private volatile String activeResponseId = null;
    private volatile String lastAnnouncedVoice = "";

    public DashScopeRealtimeSession(
            RealtimeProperties properties,
            VoiceMappingService voiceMappingService,
            ObjectMapper objectMapper,
            Consumer<String> jsonToBrowser,
            Consumer<byte[]> audioToBrowser
    ) {
        this.properties = properties;
        this.voiceMappingService = voiceMappingService;
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

    public void startLesson(JsonNode book, int pageNo, JsonNode currentSentence, String voiceStyle) {
        updateVoiceStyle(voiceStyle);
        updateContext(book, pageNo, currentSentence);
        sendSessionUpdate();
        sendTextInstruction(INTRO_PROMPT);
    }

    public void updateSentence(JsonNode book, int pageNo, JsonNode currentSentence) {
        updateContext(book, pageNo, currentSentence);
        sendSessionUpdate();
        sendTextInstruction("我们进入下一句。请先听我读一遍，再用中文解释意思，并带我跟读：" + this.currentSentenceEnglish);
    }

    public void updateVoiceStyle(String voiceStyle) {
        if (StringUtils.hasText(voiceStyle)) {
            this.voiceStyle = voiceStyle;
            sendSessionUpdate();
        }
    }

    public void readPage(int pageNo, JsonNode sentencesNode, String speed, String voiceStyle) {
        updateVoiceStyle(voiceStyle);
        this.pageNo = pageNo <= 0 ? this.pageNo : pageNo;
        StringBuilder sentences = new StringBuilder();
        if (sentencesNode != null && sentencesNode.isArray()) {
            for (JsonNode sentence : sentencesNode) {
                String text = sentence.asText("").trim();
                if (StringUtils.hasText(text)) {
                    sentences.append(text).append("\n");
                }
            }
        }
        sendTextInstruction("请使用专业英文外教声音朗读当前页所有英文句子。只朗读英文，不要中文解释。每句之间停顿一小会儿，语速自然清晰。\n" + sentences);
    }

    public void readSentence(String sentence, String speed, String voiceStyle) {
        updateVoiceStyle(voiceStyle);
        String target = StringUtils.hasText(sentence) ? sentence.trim() : this.currentSentenceEnglish;
        if ("slow".equalsIgnoreCase(speed)) {
            sendTextInstruction("请用更慢、更清楚的专业英文外教发音朗读这个句子，只朗读英文，不要解释：\n" + target);
        } else {
            sendTextInstruction("请用专业英文外教声音自然朗读下面这个英文句子，只朗读英文，不要解释：\n" + target);
        }
    }

    public void repeatSentence(String currentSentenceEnglish, String currentSentenceChinese) {
        String sentence = StringUtils.hasText(currentSentenceEnglish) ? currentSentenceEnglish : this.currentSentenceEnglish;
        if (StringUtils.hasText(currentSentenceChinese)) {
            this.currentSentenceChinese = currentSentenceChinese;
        }
        sendTextInstruction("请再次清晰朗读这个英文句子，只读英文：\n" + sentence);
    }

    public void readWord(String word, String sentence, String voiceStyle) {
        updateVoiceStyle(voiceStyle);
        sendTextInstruction("请用专业英文外教发音清晰朗读这个英文单词，只读单词本身：\n" + word);
    }

    public void assessmentFeedback(JsonNode result, String voiceStyle) {
        updateVoiceStyle(voiceStyle);
        sendTextInstruction("你是一名中文授课的少儿英语老师。请根据学生跟读评分，用中文给 6-10 岁孩子做简短反馈。要求：1. 先鼓励；2. 指出最多 2 个关键问题；3. 给出具体改进建议；4. 示范要练习的英文单词或短语；5. 不要太长；6. 不要批评孩子。评测结果：" + result.toString());
    }

    public void stopPlayback() {
        cancelActiveResponseIfNeeded();
        sendBrowserJson(Map.of("type", "playback_done", "message", "已暂停播放。"));
    }

    private void updateContext(JsonNode book, int pageNo, JsonNode currentSentence) {
        this.bookTitle = book.path("title").asText(this.bookTitle);
        this.englishTitle = book.path("englishTitle").asText(this.englishTitle);
        this.level = book.path("level").asText(this.level);
        this.pageNo = pageNo <= 0 ? 1 : pageNo;
        this.currentSentenceEnglish = currentSentence.path("english").asText("");
        this.currentSentenceChinese = currentSentence.path("chinese").asText("");
        this.currentKeywords = formatKeywords(currentSentence.path("keywords"));
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
        cancelActiveResponseIfNeeded();
        sendBrowserJson(Map.of("type", "playback_started", "message", "AI 开始朗读。"));
        sendDashScopeJson(Map.of(
                "type", "conversation.item.create",
                "item", Map.of(
                        "type", "message",
                        "role", "user",
                        "content", List.of(Map.of("type", "input_text", "text", text))
                )
        ));
        hasActiveResponse.set(true);
        sendDashScopeJson(Map.of("type", "response.create"));
    }

    private void cancelActiveResponseIfNeeded() {
        if (!hasActiveResponse.get()) {
            return;
        }
        sendDashScopeJson(Map.of("type", "response.cancel"));
        hasActiveResponse.set(false);
        activeResponseId = null;
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
        String voice = currentVoice();
        announceVoiceIfChanged(voice);
        sendDashScopeJson(Map.of(
                "type", "session.update",
                "session", Map.of(
                        "modalities", List.of("text", "audio"),
                        "voice", voice,
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

    private String currentVoice() {
        return voiceMappingService.resolveVoice(voiceStyle, properties.getDefaultVoice());
    }

    private void announceVoiceIfChanged(String voice) {
        if (!voice.equals(lastAnnouncedVoice)) {
            lastAnnouncedVoice = voice;
            sendBrowserJson(Map.of("type", "status", "message", "音色：" + voice));
        }
    }

    private String buildTutorInstructions() {
        return buildTutorInstructions(
                bookTitle,
                englishTitle,
                level,
                pageNo,
                currentSentenceEnglish,
                currentSentenceChinese,
                currentKeywords
        );
    }

    private String buildTutorInstructions(
            String bookTitle,
            String englishTitle,
            String level,
            int pageNo,
            String currentSentenceEnglish,
            String currentSentenceChinese,
            String currentKeywords
    ) {
        return "你是一名中文授课的少儿英语阅读老师，正在带 6-10 岁中国孩子阅读英文绘本。\n\n"
                + "当前绘本中文名：\n" + safe(bookTitle) + "\n\n"
                + "当前绘本英文名：\n" + safe(englishTitle) + "\n\n"
                + "当前等级：\n" + safe(level) + "\n\n"
                + "当前页码：\n第 " + pageNo + " 页\n\n"
                + "当前英文句子：\n" + safe(currentSentenceEnglish) + "\n\n"
                + "当前中文意思：\n" + safe(currentSentenceChinese) + "\n\n"
                + "当前重点词：\n" + safe(currentKeywords) + "\n\n"
                + "教学语言：\n"
                + "- 默认用中文讲解。\n"
                + "- 英文只用于朗读、跟读示范、单词示范和简单练习。\n"
                + "- 不要长时间全英文输出。\n"
                + "- 不要开放闲聊。\n\n"
                + "你的任务：\n"
                + "1. 用中文告诉学生现在学习哪一句。\n"
                + "2. 清晰朗读英文句子。\n"
                + "3. 用中文解释这句话的意思。\n"
                + "4. 讲解 1-2 个重点词。\n"
                + "5. 带学生跟读英文句子。\n"
                + "6. 学生跟读后，根据学生表现指出问题。\n"
                + "7. 给出具体改进建议。\n"
                + "8. 如果学生读得可以，鼓励并提示进入下一句。\n"
                + "9. 每次回答要短。\n"
                + "10. 一次只问一个问题。\n\n"
                + "限制：\n"
                + "- 只能围绕当前绘本内容讲解。\n"
                + "- 不要讲无关内容。\n"
                + "- 不要一次输出太多。\n"
                + "- 不要变成自由聊天。";
    }

    private String formatKeywords(JsonNode keywordsNode) {
        if (keywordsNode == null || !keywordsNode.isArray() || keywordsNode.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder();
        for (JsonNode keyword : keywordsNode) {
            if (!builder.isEmpty()) {
                builder.append("；");
            }
            builder.append(keyword.path("word").asText(""))
                    .append("：")
                    .append(keyword.path("meaning").asText(""));
        }
        return builder.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void handleDashScopeMessage(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            String type = event.path("type").asText("unknown");
            if ("response.created".equals(type) || "response.output_item.added".equals(type)) {
                hasActiveResponse.set(true);
                String responseId = event.path("response").path("id").asText(event.path("response_id").asText(""));
                activeResponseId = StringUtils.hasText(responseId) ? responseId : activeResponseId;
                return;
            }
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
            if ("response.done".equals(type) || "response.cancelled".equals(type)) {
                hasActiveResponse.set(false);
                activeResponseId = null;
                sendBrowserJson(Map.of("type", "playback_done", "message", "AI 朗读完成。"));
                return;
            }
            if ("error".equals(type) || event.has("error")) {
                hasActiveResponse.set(false);
                activeResponseId = null;
                log.warn("DashScope realtime returned error event: {}", message);
                sendBrowserJson(Map.of("type", "error", "message", message));
                return;
            }
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
