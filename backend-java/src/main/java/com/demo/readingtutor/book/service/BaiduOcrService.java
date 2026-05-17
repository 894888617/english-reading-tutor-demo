package com.demo.readingtutor.book.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "baidu")
public class BaiduOcrService implements OcrService {
    @Override
    public String recognizeText(File imageFile) {
        return "百度 OCR 接口尚未接入，请手动填写本页英文文本。";
    }
}
