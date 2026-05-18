package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.cache.AudioCacheService;
import com.demo.readingtutor.ai.types.AiTypes.TtsRequest;
import com.demo.readingtutor.ai.types.AiTypes.TtsResult;
import com.demo.readingtutor.config.AiModelProperties;
import com.demo.readingtutor.config.TtsProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "tts", name = "provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeTtsProvider implements TtsProvider {
    private static final Logger log = LoggerFactory.getLogger(DashScopeTtsProvider.class);
    private final AiModelProperties ai;
    private final TtsProperties tts;
    private final AudioCacheService cache;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public DashScopeTtsProvider(AiModelProperties ai, TtsProperties tts, AudioCacheService cache, ObjectMapper objectMapper) {
        this.ai = ai; this.tts = tts; this.cache = cache; this.objectMapper = objectMapper;
    }

    @Override
    public TtsResult synthesize(TtsRequest input) {
        if (!StringUtils.hasText(ai.getDashscopeApiKey())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DashScope API Key 未配置");
        }
        String text = input.text() == null ? "" : input.text().trim();
        if (!StringUtils.hasText(text)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "朗读文本不能为空。");
        }
        String model = tts.getModel();
        String voice = StringUtils.hasText(input.voice()) ? input.voice() : tts.getDefaultVoice();
        tts.requireVoice(voice, model);
        String language = "zh".equalsIgnoreCase(input.language()) ? "Chinese" : "English";
        String format = StringUtils.hasText(input.format()) ? input.format() : tts.getFormat();
        double speed = input.speed() == null ? tts.getSpeed() : input.speed();
        double pitch = input.pitch() == null ? tts.getPitch() : input.pitch();
        double volume = input.volume() == null ? tts.getVolume() : input.volume();
        var key = cache.buildKey(model, voice, language, speed, pitch, volume, format, text);
        var cached = cache.find(key);
        if (cached.isPresent()) {
            return new TtsResult(cached.get(), true, null, "dashscope", model, voice);
        }
        log.info("TTS synthesize request model={} voice={} language={} speed={} pitch={} volume={}", model, voice, language, speed, pitch, volume);
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", Map.of(
                            "text", text,
                            "voice", voice,
                            "language_type", language,
                            "format", format,
                            "speed", speed,
                            "pitch", pitch,
                            "volume", volume
                    )
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ai.getDashscopeBaseUrl() + "/services/aigc/multimodal-generation/generation"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + ai.getDashscopeApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "朗读音频生成失败，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String audioUrl = root.path("output").path("audio").path("url").asText("");
            String data = root.path("output").path("audio").path("data").asText("");
            byte[] bytes;
            if (StringUtils.hasText(data)) {
                bytes = java.util.Base64.getDecoder().decode(data);
            } else if (StringUtils.hasText(audioUrl)) {
                HttpRequest download = HttpRequest.newBuilder(URI.create(audioUrl)).timeout(Duration.ofSeconds(90)).GET().build();
                bytes = httpClient.send(download, HttpResponse.BodyHandlers.ofByteArray()).body();
            } else {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "朗读音频生成失败，请稍后重试");
            }
            String localUrl = cache.put(key, bytes);
            return new TtsResult(localUrl, false, null, "dashscope", model, voice);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("TTS request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "朗读音频生成失败，请稍后重试", ex);
        }
    }
}
