package com.demo.readingtutor.config;

import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.demo.readingtutor.book.config.OcrProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiConfigurationValidator {
    private static final Logger log = LoggerFactory.getLogger(AiConfigurationValidator.class);

    private final AiModelProperties ai;
    private final TtsProperties tts;
    private final OcrProperties ocr;
    private final AssessmentProperties assessment;

    public AiConfigurationValidator(AiModelProperties ai, TtsProperties tts, OcrProperties ocr, AssessmentProperties assessment) {
        this.ai = ai;
        this.tts = tts;
        this.ocr = ocr;
        this.assessment = assessment;
    }

    @PostConstruct
    public void validate() {
        if (ai.isMockEnabled()) {
            log.warn("ENABLE_AI_MOCK=true：AI mock 逻辑仅用于本地演示，请勿在生产默认启用。");
            return;
        }
        if ("dashscope".equalsIgnoreCase(tts.getProvider())) {
            require(StringUtils.hasText(ai.getDashscopeApiKey()), "TTS 配置错误：DASHSCOPE_API_KEY 不能为空");
            require(StringUtils.hasText(tts.getModel()), "TTS 配置错误：tts.model / TTS_MODEL 不能为空");
            require(StringUtils.hasText(tts.getDefaultVoice()), "TTS 配置错误：tts.default-voice / TTS_VOICE 不能为空");
        }
        if ("dashscope".equalsIgnoreCase(ocr.getProvider())) {
            require(StringUtils.hasText(ai.getDashscopeApiKey()), "OCR 配置错误：DASHSCOPE_API_KEY 不能为空");
            require(StringUtils.hasText(ocr.getModel()), "OCR 配置错误：ocr.model / OCR_MODEL 不能为空");
        }
        if ("iflytek".equalsIgnoreCase(assessment.getProvider()) && !assessment.isMockEnabled()) {
            AssessmentProperties.Vendor vendor = assessment.getVendor();
            require(StringUtils.hasText(vendor.getAppId()), "语音评测配置错误：IFLYTEK_APP_ID 不能为空");
            require(StringUtils.hasText(vendor.getApiKey()), "语音评测配置错误：IFLYTEK_API_KEY 不能为空");
            require(StringUtils.hasText(vendor.getApiSecret()), "语音评测配置错误：IFLYTEK_API_SECRET 不能为空");
            require(StringUtils.hasText(vendor.getEndpoint()), "语音评测配置错误：IFLYTEK_EVAL_ENDPOINT 不能为空");
        }
    }

    private void require(boolean ok, String message) {
        if (!ok) {
            throw new IllegalStateException(message);
        }
    }
}
