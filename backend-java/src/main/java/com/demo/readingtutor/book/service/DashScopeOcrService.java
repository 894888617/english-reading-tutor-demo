package com.demo.readingtutor.book.service;

import com.demo.readingtutor.ai.providers.OcrProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "dashscope", matchIfMissing = true)
public class DashScopeOcrService implements OcrService {
    private final OcrProvider provider;

    public DashScopeOcrService(OcrProvider provider) {
        this.provider = provider;
    }

    @Override
    public String recognizeText(File imageFile) {
        try {
            String name = imageFile.getName().toLowerCase(java.util.Locale.ROOT);
            String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";
            return provider.extractText(Files.readAllBytes(imageFile.toPath()), mime, 1).pages().get(0).text();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("OCR 识别失败，请稍后重试", ex);
        }
    }
}
