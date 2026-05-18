package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.OcrPage;
import com.demo.readingtutor.ai.types.AiTypes.OcrResult;
import com.demo.readingtutor.book.config.OcrProperties;
import com.demo.readingtutor.config.AiModelProperties;
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
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeOcrProvider implements OcrProvider {
    private static final Logger log = LoggerFactory.getLogger(DashScopeOcrProvider.class);
    private final AiModelProperties ai;
    private final OcrProperties ocr;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public DashScopeOcrProvider(AiModelProperties ai, OcrProperties ocr, ObjectMapper objectMapper) {
        this.ai = ai; this.ocr = ocr; this.objectMapper = objectMapper;
    }

    @Override
    public OcrResult extractText(byte[] fileBuffer, String mimeType, Integer pageNo) {
        if (!StringUtils.hasText(ai.getDashscopeApiKey())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OCR 服务暂未配置，请先配置 API Key");
        }
        try {
            String dataUrl = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBuffer);
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", ocr.getModel(),
                    "input", Map.of("messages", List.of(Map.of(
                            "role", "user",
                            "content", List.of(Map.of(
                                    "image", dataUrl,
                                    "min_pixels", 3072,
                                    "max_pixels", 8388608,
                                    "enable_rotate", true
                            ))
                    ))),
                    "parameters", Map.of("ocr_options", Map.of("task", "document_parsing"))
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(ai.getDashscopeBaseUrl() + "/services/aigc/multimodal-generation/generation"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + ai.getDashscopeApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("DashScope OCR failed status={} body={}", response.statusCode(), response.body());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 识别失败，请稍后重试");
            }
            JsonNode root = objectMapper.readTree(response.body());
            String text = firstText(root);
            if (!StringUtils.hasText(text)) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 识别失败：未识别到英文文本");
            }
            return new OcrResult(List.of(new OcrPage(pageNo == null ? 1 : pageNo, clean(text), List.of(), null)));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("OCR request failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 识别失败，请稍后重试", ex);
        }
    }

    private String firstText(JsonNode root) {
        JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
        if (content.isArray()) {
            for (JsonNode item : content) {
                String text = item.path("text").asText("");
                if (StringUtils.hasText(text)) return text;
            }
        }
        return root.path("output").path("text").asText("");
    }

    private String clean(String text) {
        return text.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```$", "").trim();
    }
}
