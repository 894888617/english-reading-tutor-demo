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

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
    public SpeechEvalResult evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, String contentType, String originalFilename) {
        var vendor = properties.getVendor();
        if (!StringUtils.hasText(vendor.getAppId()) || !StringUtils.hasText(vendor.getApiKey()) || !StringUtils.hasText(vendor.getApiSecret()) || !StringUtils.hasText(vendor.getEndpoint())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "语音评测服务未配置");
        }
        try {
            PreparedAudio preparedAudio = prepareAudio(audioFile, contentType, originalFilename);
            byte[] audioBytes = preparedAudio.audioBytes();
            String audioBase64 = Base64.getEncoder().encodeToString(audioBytes);
            if (!StringUtils.hasText(audioBase64)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "录音编码失败");
            }
            log.info("Speech evaluation request endpoint={} referenceText={} audioBytesLength={} audioBase64Length={} aue={}",
                    vendor.getEndpoint(), referenceText, audioBytes.length, audioBase64.length(), preparedAudio.aue());

            JsonNode root;
            if (vendor.getEndpoint().startsWith("wss://") || vendor.getEndpoint().startsWith("ws://")) {
                root = webSocketClient.evaluate(audioBytes, referenceText, language, sentenceId, userId, vendor, preparedAudio.aue(), preparedAudio.auf());
            } else {
                root = evaluateByHttp(audioBase64, referenceText, language, sentenceId, userId, vendor, preparedAudio.aue(), preparedAudio.auf());
            }
            return parseResult(root);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Speech evaluation request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "评分失败，请重新录音", ex);
        }
    }

    private JsonNode evaluateByHttp(String audioBase64, String referenceText, String language, String sentenceId, String userId, AssessmentProperties.Vendor vendor, String aue, String auf) throws Exception {
        Map<String, Object> business = Map.of(
                "category", "read_sentence",
                "sub", "ise",
                "ent", "zh".equalsIgnoreCase(language) || "Chinese".equalsIgnoreCase(language) ? "cn_vip" : "en_vip",
                "cmd", "ssb",
                "auf", StringUtils.hasText(auf) ? auf : "audio/L16;rate=16000",
                "aue", StringUtils.hasText(aue) ? aue : "raw",
                "text", referenceText == null ? "" : referenceText
        );
        log.info("Speech evaluation HTTP request business={}", business);
        String body = objectMapper.writeValueAsString(Map.of(
                "common", Map.of("app_id", vendor.getAppId()),
                "business", business,
                "data", Map.of(
                        "status", 2,
                        "data", audioBase64
                )
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

    private PreparedAudio prepareAudio(byte[] audioFile, String contentType, String originalFilename) throws Exception {
        byte[] audioBytes = audioFile == null ? new byte[0] : audioFile;
        if (audioBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录音文件为空，请重新录音");
        }
        AudioFormat format = detectAudioFormat(audioBytes, contentType, originalFilename);
        return switch (format) {
            case RAW_PCM -> new PreparedAudio(audioBytes, "raw", "audio/L16;rate=16000");
            case MP3 -> new PreparedAudio(audioBytes, "lame", "audio/L16;rate=16000");
            case WEBM, WAV, MP4, UNKNOWN -> new PreparedAudio(transcodeToRawPcm(audioBytes, format), "raw", "audio/L16;rate=16000");
        };
    }

    private AudioFormat detectAudioFormat(byte[] audioBytes, String contentType, String originalFilename) {
        String normalizedContentType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String normalizedFilename = originalFilename == null ? "" : originalFilename.toLowerCase(Locale.ROOT);
        if (normalizedContentType.contains("mpeg") || normalizedContentType.contains("mp3") || normalizedFilename.endsWith(".mp3") || startsWith(audioBytes, 'I', 'D', '3') || isMp3Frame(audioBytes)) {
            return AudioFormat.MP3;
        }
        if (normalizedContentType.contains("webm") || normalizedFilename.endsWith(".webm") || isEbml(audioBytes)) {
            return AudioFormat.WEBM;
        }
        if (normalizedContentType.contains("wav") || normalizedContentType.contains("wave") || normalizedFilename.endsWith(".wav") || startsWith(audioBytes, 'R', 'I', 'F', 'F')) {
            return AudioFormat.WAV;
        }
        if (normalizedContentType.contains("mp4") || normalizedFilename.endsWith(".mp4") || normalizedFilename.endsWith(".m4a")) {
            return AudioFormat.MP4;
        }
        if (normalizedContentType.contains("pcm") || normalizedContentType.contains("octet-stream") || normalizedFilename.endsWith(".pcm") || normalizedFilename.endsWith(".raw")) {
            return AudioFormat.RAW_PCM;
        }
        return AudioFormat.UNKNOWN;
    }

    private byte[] transcodeToRawPcm(byte[] audioBytes, AudioFormat sourceFormat) throws Exception {
        if (!ffmpegAvailable()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前上传音频格式为 " + sourceFormat.name().toLowerCase(Locale.ROOT) + "，需要先安装 ffmpeg 转码为 16k 单声道 PCM，或直接上传 raw PCM / mp3。");
        }
        Path input = Files.createTempFile("speech-eval-input-", ".audio");
        try {
            Files.write(input, audioBytes);
            Process process = new ProcessBuilder("ffmpeg", "-hide_banner", "-loglevel", "error", "-i", input.toString(), "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "1", "-ar", "16000", "pipe:1")
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            process.getInputStream().transferTo(output);
            if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频转码超时，请缩短录音后重试。");
            }
            if (process.exitValue() != 0 || output.size() == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频格式不支持或转码失败，请上传 raw PCM / mp3，webm 请先转码。");
            }
            return output.toByteArray();
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private boolean ffmpegAvailable() {
        try {
            Process process = new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start();
            return process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean startsWith(byte[] bytes, char... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((bytes[i] & 0xff) != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isEbml(byte[] bytes) {
        return bytes.length >= 4 && (bytes[0] & 0xff) == 0x1a && (bytes[1] & 0xff) == 0x45 && (bytes[2] & 0xff) == 0xdf && (bytes[3] & 0xff) == 0xa3;
    }

    private boolean isMp3Frame(byte[] bytes) {
        return bytes.length >= 2 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xe0) == 0xe0;
    }

    private enum AudioFormat { RAW_PCM, MP3, WEBM, WAV, MP4, UNKNOWN }

    private record PreparedAudio(byte[] audioBytes, String aue, String auf) {}

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
