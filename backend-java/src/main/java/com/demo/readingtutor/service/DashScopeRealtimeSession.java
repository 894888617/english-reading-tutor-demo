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
    private static final String INTRO_PROMPT = "Please start the reading lesson. Read the current sentence first, then ask me one simple question.";

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
    private volatile String currentSentence = "";

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
            sendBrowserError("DASHSCOPE_API_KEY is not configured. Set it before starting the backend.");
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
                        sendBrowserError("Failed to connect to DashScope realtime WebSocket: " + error.getMessage());
                    } else {
                        this.webSocket = socket;
                    }
                });
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket = webSocket;
        open.set(true);
        sendBrowserJson(Map.of("type", "status", "message", "connected_to_dashscope"));
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
        sendBrowserJson(Map.of("type", "status", "message", "dashscope_closed", "code", statusCode, "reason", reason));
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        open.set(false);
        log.warn("DashScope realtime WebSocket error", error);
        sendBrowserError("DashScope realtime WebSocket error: " + error.getMessage());
    }

    public void startLesson(String storyTitle, String currentSentence) {
        this.storyTitle = storyTitle == null ? "" : storyTitle;
        this.currentSentence = currentSentence == null ? "" : currentSentence;
        sendSessionUpdate();
        sendTextInstruction(INTRO_PROMPT);
    }

    public void updateSentence(String storyTitle, String currentSentence) {
        this.storyTitle = storyTitle == null ? this.storyTitle : storyTitle;
        this.currentSentence = currentSentence == null ? "" : currentSentence;
        sendSessionUpdate();
        sendTextInstruction("Now move to the new sentence. Please read it once and ask one simple question: " + this.currentSentence);
    }

    public void repeatSentence(String currentSentence) {
        String sentence = StringUtils.hasText(currentSentence) ? currentSentence : this.currentSentence;
        sendTextInstruction("Please read the current sentence slowly and clearly: " + sentence);
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
                        "temperature", 0.7
                )
        ));
    }

    private String buildTutorInstructions() {
        return "You are an English reading tutor for Chinese children aged 6-10. "
                + "Help the student read the current story sentence by sentence. Read clearly, explain difficult words, "
                + "ask one simple question at a time, correct mistakes gently, and encourage the student. Use simple English. "
                + "If the student does not understand, explain briefly in Chinese. Stay focused on the current story. "
                + "Current story: " + safe(storyTitle) + ". Current sentence: " + safe(currentSentence);
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
            sendBrowserError("Failed to parse DashScope message: " + ex.getMessage());
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
            sendBrowserError("Failed to serialize DashScope event: " + ex.getMessage());
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
