package com.demo.readingtutor.book.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;

@Service
@ConditionalOnProperty(prefix = "ocr", name = "provider", havingValue = "tencent")
public class TencentOcrService implements OcrService {
    @Override
    public String recognizeText(File imageFile) {
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "OCR 服务暂未配置，请先配置 API Key");
    }
}
