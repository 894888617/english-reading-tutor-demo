package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import org.springframework.web.multipart.MultipartFile;

public class VendorSpeechAssessmentService implements SpeechAssessmentService {
    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText) {
        throw new UnsupportedOperationException("当前语音评测服务未配置，已使用 Demo 模拟评分。");
    }

    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText, String recognizedText) {
        throw new UnsupportedOperationException("当前语音评测服务未配置，已使用 Demo 模拟评分。");
    }
}
