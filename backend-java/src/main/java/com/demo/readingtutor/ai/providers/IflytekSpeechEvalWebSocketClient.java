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
    private static final int AUDIO_CHUNK_SIZE_BYTES = 1280;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public IflytekSpeechEvalWebSocketClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, AssessmentProperties.Vendor vendor) {
        return evaluate(audioFile, referenceText, language, sentenceId, userId, vendor, "raw", "audio/L16;rate=16000");
    }

    public JsonNode evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, AssessmentProperties.Vendor vendor, String aue, String auf) {
        URI signedUri = buildSignedUri(vendor.getEndpoint(), vendor.getApiKey(), vendor.getApiSecret());
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<String> messages = new ArrayList<>();
        AtomicReference<String> finalResultMessage = new AtomicReference<>();
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
                        String responseMessage = node.path("message").asText("");
                        log.info("Iflytek speech evaluation response code={} message={}", code, responseMessage);
                        if (code != 0) {
                            if (responseMessage.contains("param validate error")) {
                                log.warn("Iflytek param validate error; sent request field structure={}", safeFieldStructure(audioFile, referenceText, language, sentenceId, userId, vendor.getAppId()));
                            }
                            failure.set(new ResponseStatusException(mapIflytekStatus(code), mapIflytekMessage(code, responseMessage)));
                            done.countDown();
                        } else {
                            String finalPayload = node.path("data").path("data").asText("");
                            int status = node.path("data").path("status").asInt(node.path("status").asInt(-1));
                            if (StringUtils.hasText(finalPayload)) {
                                finalResultMessage.set(message);
                                done.countDown();
                            } else if (status == 2) {
                                failure.set(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测未返回最终评分结果"));
                                done.countDown();
                            }
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
            List<String> requestFrames = buildRequestFrames(audioFile, referenceText, language, sentenceId, userId, vendor.getAppId(), vendor.getEndpoint(), aue, auf);
            WebSocket webSocket = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(20))
                    .buildAsync(signedUri, listener)
                    .join();
            for (String requestFrame : requestFrames) {
                webSocket.sendText(requestFrame, true).join();
            }
            if (!done.await(90, TimeUnit.SECONDS)) {
                webSocket.abort();
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测超时：未收到最终评分结果");
            }
            if (failure.get() != null) {
                Throwable error = failure.get();
                if (error instanceof ResponseStatusException ex) {
                    throw ex;
                }
                log.warn("Iflytek speech evaluation websocket failed", error);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测服务连接失败", error);
            }
            if (!StringUtils.hasText(finalResultMessage.get())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "语音评测未返回最终评分结果");
            }
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
            return combineMessages(messages, finalResultMessage.get());
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

    private List<String> buildRequestFrames(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, String appId, String endpoint, String aue, String auf) throws Exception {
        byte[] audioBytes = audioFile == null ? new byte[0] : audioFile;
        if (audioBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录音文件为空，请重新录音");
        }
        int audioBase64Length = Base64.getEncoder().encodeToString(audioBytes).length();
        if (audioBase64Length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "录音编码失败");
        }

        Map<String, Object> common = Map.of("app_id", appId);
        Map<String, Object> parameterBusiness = baseBusiness(referenceText, language, sentenceId, userId, aue, auf);
        parameterBusiness.put("cmd", "ssb");
        log.info("Iflytek speech evaluation request endpoint={} referenceText={} audioBytesLength={} audioBase64Length={} business={}",
                endpoint, referenceText, audioBytes.length, audioBase64Length, parameterBusiness);

        List<String> frames = new ArrayList<>();
        frames.add(objectMapper.writeValueAsString(Map.of(
                "common", common,
                "business", parameterBusiness,
                "data", Map.of("status", 0)
        )));

        List<byte[]> chunks = splitAudio(audioBytes);
        for (int i = 0; i < chunks.size(); i++) {
            boolean first = i == 0;
            boolean last = i == chunks.size() - 1;
            Map<String, Object> audioBusiness = new LinkedHashMap<>();
            audioBusiness.put("cmd", "auw");
            audioBusiness.put("aus", first ? 1 : last ? 4 : 2);
            String chunkBase64 = Base64.getEncoder().encodeToString(chunks.get(i));
            log.info("[IflytekEval] send audio frame aus={} status={} audioBase64Length={}", audioBusiness.get("aus"), last ? 2 : 1, chunkBase64.length());
            frames.add(objectMapper.writeValueAsString(Map.of(
                    "business", audioBusiness,
                    "data", Map.of(
                            "status", last ? 2 : 1,
                            "data", chunkBase64
                    )
            )));
        }
        return frames;
    }

    private Map<String, Object> baseBusiness(String referenceText, String language, String sentenceId, String userId, String aue, String auf) {
        Map<String, Object> business = new LinkedHashMap<>();
        business.put("category", "read_sentence");
        business.put("sub", "ise");
        business.put("ent", normalizeLanguage(language));
        business.put("auf", StringUtils.hasText(auf) ? auf : "audio/L16;rate=16000");
        business.put("aue", StringUtils.hasText(aue) ? aue : "raw");
        business.put("text", referenceText == null ? "" : referenceText);
        if (StringUtils.hasText(sentenceId)) {
            business.put("sentence_id", sentenceId);
        }
        if (StringUtils.hasText(userId)) {
            business.put("user_id", userId);
        }
        return business;
    }

    private List<byte[]> splitAudio(byte[] audioBytes) {
        List<byte[]> chunks = new ArrayList<>();
        int chunkSize = audioBytes.length > 1 && audioBytes.length <= AUDIO_CHUNK_SIZE_BYTES
                ? Math.max(1, audioBytes.length / 2)
                : AUDIO_CHUNK_SIZE_BYTES;
        for (int offset = 0; offset < audioBytes.length; offset += chunkSize) {
            int length = Math.min(chunkSize, audioBytes.length - offset);
            byte[] chunk = new byte[length];
            System.arraycopy(audioBytes, offset, chunk, 0, length);
            chunks.add(chunk);
        }
        return chunks;
    }

    private String safeFieldStructure(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, String appId) {
        int audioBytesLength = audioFile == null ? 0 : audioFile.length;
        int audioBase64Length = audioBytesLength == 0 ? 0 : Base64.getEncoder().encodeToString(audioFile).length();
        Map<String, Object> parameterBusiness = baseBusiness(referenceText, language, sentenceId, userId, "raw", "audio/L16;rate=16000");
        parameterBusiness.put("cmd", "ssb");
        Map<String, Object> firstAudioBusiness = new LinkedHashMap<>();
        firstAudioBusiness.put("cmd", "auw");
        firstAudioBusiness.put("aus", 1);
        Map<String, Object> safeAudioData = new LinkedHashMap<>();
        safeAudioData.put("status", 1);
        safeAudioData.put("data", Map.of("base64Length", audioBase64Length));
        Map<String, Object> structure = new LinkedHashMap<>();
        structure.put("parameterFrame", Map.of(
                "common", Map.of("app_id", StringUtils.hasText(appId) ? "configured" : "missing"),
                "business", parameterBusiness,
                "data", Map.of("status", 0)
        ));
        structure.put("audioFrame", Map.of(
                "business", firstAudioBusiness,
                "data", safeAudioData,
                "audioBytesLength", audioBytesLength
        ));
        try {
            return objectMapper.writeValueAsString(structure);
        } catch (Exception ex) {
            return structure.toString();
        }
    }

    private JsonNode combineMessages(List<String> messages, String finalMessage) throws Exception {
        List<JsonNode> nodes = new ArrayList<>();
        for (String message : messages) {
            nodes.add(objectMapper.readTree(message));
        }
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("messages", nodes);
        raw.put("final", StringUtils.hasText(finalMessage) ? objectMapper.readTree(finalMessage) : Map.of());
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
