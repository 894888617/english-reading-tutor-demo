package com.demo.readingtutor.ws;

import com.demo.readingtutor.config.RealtimeProperties;
import com.demo.readingtutor.config.TtsProperties;
import com.demo.readingtutor.service.DashScopeRealtimeSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RealtimeWebSocketHandler extends BinaryWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    private final RealtimeProperties properties;
    private final ObjectMapper objectMapper;
    private final TtsProperties ttsProperties;
    private final Map<String, DashScopeRealtimeSession> dashScopeSessions = new ConcurrentHashMap<>();

    public RealtimeWebSocketHandler(RealtimeProperties properties, TtsProperties ttsProperties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.ttsProperties = ttsProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession browserSession) {
        DashScopeRealtimeSession dashScopeSession = new DashScopeRealtimeSession(
                properties,
                ttsProperties,
                objectMapper,
                json -> sendText(browserSession, json),
                audio -> sendBinary(browserSession, audio)
        );
        dashScopeSessions.put(browserSession.getId(), dashScopeSession);
        sendJson(browserSession, Map.of("type", "status", "message", "已连接到 Java 后端。"));
        dashScopeSession.connect();
    }

    @Override
    protected void handleTextMessage(WebSocketSession browserSession, TextMessage message) {
        DashScopeRealtimeSession dashScopeSession = dashScopeSessions.get(browserSession.getId());
        if (dashScopeSession == null) {
            sendJson(browserSession, Map.of("type", "error", "message", "实时语音会话尚未初始化。"));
            return;
        }

        try {
            JsonNode payload = objectMapper.readTree(message.getPayload());
            String type = payload.path("type").asText("");
            switch (type) {
                case "start_lesson" -> dashScopeSession.startLesson(
                        payload.path("book"),
                        payload.path("pageNo").asInt(1),
                        payload.path("currentSentence"),
                        payload.path("voiceStyle").asText("")
                );
                case "session_update" -> dashScopeSession.updateVoiceStyle(payload.path("voiceStyle").asText(""));
                case "read_page" -> dashScopeSession.readPage(
                        payload.path("pageNo").asInt(1),
                        payload.path("sentences"),
                        payload.path("speed").asText("normal"),
                        payload.path("voiceStyle").asText("")
                );
                case "read_sentence" -> dashScopeSession.readSentence(
                        payload.path("sentence").asText(""),
                        payload.path("speed").asText("normal"),
                        payload.path("voiceStyle").asText("")
                );
                case "update_sentence" -> dashScopeSession.updateSentence(
                        payload.path("book"),
                        payload.path("pageNo").asInt(1),
                        payload.path("currentSentence")
                );
                case "repeat_sentence" -> dashScopeSession.repeatSentence(
                        payload.path("sentence").asText(payload.path("currentSentence").path("english").asText("")),
                        payload.path("currentSentence").path("chinese").asText("")
                );
                case "read_word" -> dashScopeSession.readWord(
                        payload.path("word").asText(""),
                        payload.path("sentence").asText(""),
                        payload.path("voiceStyle").asText("")
                );
                case "assessment_feedback" -> dashScopeSession.assessmentFeedback(
                        payload.path("result"),
                        payload.path("voiceStyle").asText("")
                );
                case "stop_playback" -> dashScopeSession.stopPlayback();
                case "stop" -> {
                    dashScopeSession.close();
                    sendJson(browserSession, Map.of("type", "status", "message", "已结束会话。"));
                    browserSession.close(CloseStatus.NORMAL);
                }
                default -> sendJson(browserSession, Map.of("type", "error", "message", "不支持的控制消息类型：" + type));
            }
        } catch (Exception ex) {
            log.warn("Failed to handle browser realtime control message", ex);
            sendJson(browserSession, Map.of("type", "error", "message", "控制消息格式不正确：" + ex.getMessage()));
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession browserSession, BinaryMessage message) {
        DashScopeRealtimeSession dashScopeSession = dashScopeSessions.get(browserSession.getId());
        if (dashScopeSession == null) {
            return;
        }
        ByteBuffer payload = message.getPayload();
        byte[] pcmBytes = new byte[payload.remaining()];
        payload.get(pcmBytes);
        dashScopeSession.sendAudio(pcmBytes);
    }

    @Override
    public void handleTransportError(WebSocketSession browserSession, Throwable exception) {
        log.warn("Browser realtime WebSocket transport error. sessionId={}", browserSession.getId(), exception);
        DashScopeRealtimeSession dashScopeSession = dashScopeSessions.remove(browserSession.getId());
        if (dashScopeSession != null) {
            dashScopeSession.close();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession browserSession, CloseStatus status) {
        DashScopeRealtimeSession dashScopeSession = dashScopeSessions.remove(browserSession.getId());
        if (dashScopeSession != null) {
            dashScopeSession.close();
        }
    }

    private void sendJson(WebSocketSession session, Object payload) {
        try {
            sendText(session, objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.warn("Failed to serialize browser WebSocket message", ex);
        }
    }

    private void sendText(WebSocketSession session, String json) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ex) {
                log.warn("Failed to send text message to browser", ex);
            }
        }
    }

    private void sendBinary(WebSocketSession session, byte[] bytes) {
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new BinaryMessage(bytes));
            } catch (IOException ex) {
                log.warn("Failed to send audio message to browser", ex);
            }
        }
    }
}
