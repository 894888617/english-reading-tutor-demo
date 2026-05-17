package com.demo.readingtutor.book.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "tencent")
public class TencentOcrService implements OcrService {
    @Override
    public String recognizeText(File imageFile) {
        return "腾讯 OCR 接口尚未接入，请手动填写本页英文文本。";
    }
}
