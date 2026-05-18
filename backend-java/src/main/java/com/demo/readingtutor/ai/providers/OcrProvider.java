package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.OcrResult;

public interface OcrProvider {
    OcrResult extractText(byte[] fileBuffer, String mimeType, Integer pageNo);
}
