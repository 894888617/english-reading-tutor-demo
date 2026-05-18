package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class IflytekSpeechEvalWebSocketClient {
    private static final Logger log = LoggerFactory.getLogger(IflytekSpeechEvalWebSocketClient.class);
    private static final DateTimeFormatter RFC_1123 = DateTimeFormatter.RFC_1123_DATE_TIME;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public IflytekSpeechEvalWebSocketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, AssessmentProperties.Vendor vendor) {
        URI signedUri = buildSignedUri(vendor.getEndpoint(), vendor.getApiKey(), vendor.getApiSecret());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> messages = new ArrayList<>();
        StringBuilder partial = new StringBuilder();

        WebSocket.Listener listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                partial.append(data);
                if (last) {
                    String message = partial.toString();
                    partial.setLength(0);
                    messages.add(message);
                    try {
                        JsonNode node = objectMapper.readTree(message);
                        int code = node.path("code").asInt(0);
                        if (code != 0) {
                            failure.set(new ResponseStatusException(mapIflytekStatus(code), mapIflytekMessage(code, node.path("message").asText(""))));
                            done.countDown();
                        } else if (node.path("data").path("status").asInt(-1) == 2 || node.path("status").asInt(-1) == 2) {
                            done.countDown();
                        }
                    } catch (Exception ex) {
                        failure.set(ex);
                        done.countDown();
                    }
                }
                webSocket.request(1);
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                failure.set(error);
                done.countDown();
            }
        };

        try {
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .buildAsync(signedUri, listener)
                    .join();
            webSocket.sendText(buildRequest(audioFile, referenceText, language, sentenceId, userId, vendor.getAppId()), true).join();
            if (!done.await(90, TimeUnit.SECONDS)) {
                webSocket.abort();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测服务连接失败");
            }
            if (failure.get() != null) {
                Throwable error = failure.get();
                if (error instanceof ResponseStatusException ex) {
                    throw ex;
                }
                log.warn("Iflytek speech evaluation websocket failed", error);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测服务连接失败", error);
            }
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            return combineMessages(messages);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (CompletionException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage();
            if (message.contains("401") || message.contains("403")) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "语音评测鉴权失败，请检查 API Key / API Secret", ex);
            }
            log.warn("Iflytek speech evaluation websocket request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测服务连接失败", ex);
        } catch (Exception ex) {
            log.warn("Iflytek speech evaluation websocket request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测服务连接失败", ex);
        }
    }

    private URI buildSignedUri(String endpoint, String apiKey, String apiSecret) {
        try {
            URI uri = URI.create(endpoint);
            String host = uri.getHost();
            String path = StringUtils.hasText(uri.getRawPath()) ? uri.getRawPath() : "/";
            if (StringUtils.hasText(uri.getRawQuery())) {
                path += "?" + uri.getRawQuery();
            }
            String date = RFC_1123.format(ZonedDateTime.now(java.time.ZoneOffset.UTC));
            String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + "GET " + path + " HTTP/1.1";
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getEncoder().encodeToString(mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8)));
            String authorizationOrigin = String.format(Locale.ROOT,
                    "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                    apiKey, signature);
            String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));
            String separator = endpoint.contains("?") ? "&" : "?";
            return URI.create(endpoint + separator
                    + "authorization=" + encode(authorization)
                    + "&date=" + encode(date)
                    + "&host=" + encode(host));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "语音评测鉴权失败，请检查 API Key / API Secret", ex);
        }
    }

    private String buildRequest(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, String appId) throws Exception {
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("category", "read_sentence");
        business.put("sub", "ise");
        business.put("ent", normalizeLanguage(language));
        business.put("cmd", "ssb");
        business.put("auf", "audio/L16;rate=16000");
        business.put("aue", "raw");
        business.put("text", referenceText == null ? "" : referenceText);
        if (StringUtils.hasText(sentenceId)) {
            business.put("sentence_id", sentenceId);
        }
        if (StringUtils.hasText(userId)) {
            business.put("user_id", userId);
        }
        return objectMapper.writeValueAsString(Map.of(
                "common", Map.of("app_id", appId),
                "business", business,
                "data", Map.of(
                        "status", 2,
                        "format", "audio/L16;rate=16000",
                        "audio", Base64.getEncoder().encodeToString(audioFile),
                        "encoding", "raw"
                )
        ));
    }

    private JsonNode combineMessages(List<String> messages) throws Exception {
        List<JsonNode> nodes = new ArrayList<>();
        for (String message : messages) {
            nodes.add(objectMapper.readTree(message));
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("messages", nodes);
        raw.put("final", nodes.isEmpty() ? Map.of() : nodes.get(nodes.size() - 1));
        return objectMapper.valueToTree(raw);
    }

    private HttpStatus mapIflytekStatus(int code) {
        return code == 10105 || code == 10106 || code == 10107 || code == 10110 ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
    }

    private String mapIflytekMessage(int code, String message) {
        if (code == 10105 || code == 10106 || code == 10107 || code == 10110) {
            return "语音评测鉴权失败，请检查 API Key / API Secret";
        }
        return StringUtils.hasText(message) ? "语音评测服务连接失败：" + message : "语音评测服务连接失败";
    }

    private String normalizeLanguage(String language) {
        return "zh".equalsIgnoreCase(language) || "Chinese".equalsIgnoreCase(language) ? "cn_vip" : "en_vip";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
