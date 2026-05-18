package com.demo.readingtutor.book.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;

import java.io.File;

@Service
@ConditionalOnExpression("${ai.mock-enabled:false}")
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockOcrService implements OcrService {
    public static final String MESSAGE = "当前图片需要 OCR 识别，但 OCR 服务尚未配置。请手动输入本页英文内容。";

    @Override
    public String recognizeText(File imageFile) {
        return MESSAGE;
    }
}
