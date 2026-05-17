package com.demo.readingtutor.assessment.controller;

import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.service.SpeechAssessmentService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AssessmentController {
    private final SpeechAssessmentService assessmentService;
    private final AssessmentProperties properties;

    public AssessmentController(SpeechAssessmentService assessmentService, AssessmentProperties properties) {
        this.assessmentService = assessmentService;
        this.properties = properties;
    }

    @PostMapping(value = "/api/assessment/reading", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReadingAssessmentResult assessReading(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("targetText") String targetText,
            @RequestParam(value = "bookId", required = false) String bookId,
            @RequestParam(value = "pageNo", required = false, defaultValue = "1") int pageNo,
            @RequestParam(value = "sentenceIndex", required = false, defaultValue = "0") int sentenceIndex,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        if (audio == null || audio.isEmpty()) {
            throw new IllegalArgumentException("评分失败：没有收到录音文件，请重新录音。");
        }
        if (!StringUtils.hasText(targetText)) {
            throw new IllegalArgumentException("当前没有可朗读的句子。");
        }
        long maxBytes = Math.max(1, properties.getMaxAudioSizeMb()) * 1024L * 1024L;
        if (audio.getSize() > maxBytes) {
            throw new IllegalArgumentException("录音时间太长或文件过大，请控制在 30 秒以内。");
        }
        return assessmentService.assess(audio, targetText, recognizedText);
    }
}
