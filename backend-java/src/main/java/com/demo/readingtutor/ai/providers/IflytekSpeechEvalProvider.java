package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.SpeechEvalResult;
import com.demo.readingtutor.ai.types.AiTypes.SpeechWord;
import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnExpression("!${ai.mock-enabled:false}")
@ConditionalOnProperty(prefix = "assessment", name = "provider", havingValue = "iflytek", matchIfMissing = true)
public class IflytekSpeechEvalProvider implements SpeechEvalProvider {
    private static final Logger log = LoggerFactory.getLogger(IflytekSpeechEvalProvider.class);
    private final AssessmentProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();
    private final IflytekSpeechEvalWebSocketClient webSocketClient;

    public IflytekSpeechEvalProvider(AssessmentProperties properties, ObjectMapper objectMapper, IflytekSpeechEvalWebSocketClient webSocketClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webSocketClient = webSocketClient;
    }

    @Override
    public SpeechEvalResult evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId) {
        var vendor = properties.getVendor();
        if (!StringUtils.hasText(vendor.getAppId()) || !StringUtils.hasText(vendor.getApiKey()) || !StringUtils.hasText(vendor.getApiSecret()) || !StringUtils.hasText(vendor.getEndpoint())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "语音评测服务未配置");
        }
        try {
            JsonNode root;
            if (vendor.getEndpoint().startsWith("wss://") || vendor.getEndpoint().startsWith("ws://")) {
                root = webSocketClient.evaluate(audioFile, referenceText, language, sentenceId, userId, vendor);
            } else {
                root = evaluateByHttp(audioFile, referenceText, language, sentenceId, userId, vendor);
            }
            return parseResult(root);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Speech evaluation request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "评分失败，请重新录音", ex);
        }
    }

    private JsonNode evaluateByHttp(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, AssessmentProperties.Vendor vendor) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "appId", vendor.getAppId(),
                "referenceText", referenceText,
                "language", language,
                "sentenceId", sentenceId == null ? "" : sentenceId,
                "userId", userId == null ? "" : userId,
                "audio", Base64.getEncoder().encodeToString(audioFile)
        ));
        HttpRequest request = HttpRequest.newBuilder(URI.create(vendor.getEndpoint()))
                .timeout(Duration.ofSeconds(90))
                .header("Content-Type", "application/json")
                .header("X-API-Key", vendor.getApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            log.warn("Speech evaluation failed status={} body={}", response.statusCode(), response.body());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "评分失败，请重新录音");
        }
        return objectMapper.readTree(response.body());
    }

    private SpeechEvalResult parseResult(JsonNode root) {
        root = unwrapIflytekWebSocketResult(root);
        JsonNode scores = root.has("scores") ? root.path("scores") : root;
        int accuracy = score(scores, "accuracyScore", "accuracy");
        int fluency = score(scores, "fluencyScore", "fluency");
        int completeness = score(scores, "completenessScore", "completeness");
        int clarity = score(scores, "clarityScore", "clarity");
        int total = scores.path("totalScore").asInt(scores.path("total").asInt(Math.round((accuracy + fluency + completeness + clarity) / 4f)));
        List<SpeechWord> words = new ArrayList<>();
        JsonNode wordNode = root.has("words") ? root.path("words") : root.path("data").path("words");
        if (wordNode.isArray()) {
            for (JsonNode w : wordNode) {
                int wordScore = w.path("score").asInt(0);
                words.add(new SpeechWord(
                        w.path("word").asText(""),
                        wordScore,
                        w.path("correct").asBoolean(wordScore >= 60),
                        w.has("startTime") ? w.path("startTime").asDouble() : null,
                        w.has("endTime") ? w.path("endTime").asDouble() : null,
                        List.of(),
                        w.path("expectedPhoneme").asText(null),
                        w.path("actualPhoneme").asText(null),
                        w.path("actualWord").asText(w.path("actual").asText(null))
                ));
            }
        } else if (StringUtils.hasText(root.path("rawXml").asText(""))) {
            words.addAll(parseXmlWords(root.path("rawXml").asText()));
        }
        return new SpeechEvalResult(total, accuracy, fluency, completeness, clarity, words, root);
    }

    private List<SpeechWord> parseXmlWords(String xml) {
        List<SpeechWord> words = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<word\\b([^>]*)>").matcher(xml);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            String word = firstAttr(attrs, "content", "word", "text");
            if (!StringUtils.hasText(word)) {
                continue;
            }
            int wordScore = firstScoreAttr(attrs, "total_score", "score", "accuracy_score");
            words.add(new SpeechWord(word, wordScore, wordScore >= 60, null, null, List.of(), null, null, null));
        }
        return words;
    }

    private String firstAttr(String attrs, String... names) {
        for (String name : names) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(name + "=\"([^\"]*)\"").matcher(attrs);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "";
    }

    private int firstScoreAttr(String attrs, String... names) {
        for (String name : names) {
            int score = extractAttr(attrs, name);
            if (score > 0) return score;
        }
        return 0;
    }

    private JsonNode unwrapIflytekWebSocketResult(JsonNode root) {
        JsonNode finalNode = root.path("final");
        if (!finalNode.isMissingNode() && !finalNode.isEmpty()) {
            String base64Result = finalNode.path("data").path("data").asText("");
            if (StringUtils.hasText(base64Result)) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(base64Result), StandardCharsets.UTF_8);
                    return objectMapper.createObjectNode()
                            .put("rawXml", decoded)
                            .put("totalScore", extractXmlScore(decoded, "total_score", "total"))
                            .put("accuracyScore", extractXmlScore(decoded, "accuracy_score", "accuracy"))
                            .put("fluencyScore", extractXmlScore(decoded, "fluency_score", "fluency"))
                            .put("completenessScore", extractXmlScore(decoded, "integrity_score", "completeness"))
                            .put("clarityScore", extractXmlScore(decoded, "standard_score", "clarity"));
                } catch (Exception ex) {
                    log.warn("Failed to decode Iflytek speech evaluation payload", ex);
                }
            }
            return finalNode;
        }
        return root;
    }

    private int extractXmlScore(String xml, String primaryAttr, String fallbackAttr) {
        int score = extractAttr(xml, primaryAttr);
        if (score > 0) return score;
        score = extractAttr(xml, fallbackAttr);
        return score > 0 ? score : 0;
    }

    private int extractAttr(String xml, String attr) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(attr + "=\"([0-9.]+)\"").matcher(xml);
        if (matcher.find()) {
            return Math.max(0, Math.min(100, (int) Math.round(Double.parseDouble(matcher.group(1)))));
        }
        return 0;
    }

    private int score(JsonNode scores, String primary, String fallback) {
        int value = scores.path(primary).asInt(scores.path(fallback).asInt(0));
        return Math.max(0, Math.min(100, value));
    }
}
