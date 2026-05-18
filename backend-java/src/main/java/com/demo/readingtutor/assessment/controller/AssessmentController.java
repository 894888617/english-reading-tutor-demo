package com.demo.readingtutor.assessment.controller;

import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.service.EvaluationRecordService;
import com.demo.readingtutor.assessment.service.SpeechAssessmentService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AssessmentController {
    private final SpeechAssessmentService assessmentService;
    private final AssessmentProperties properties;
    private final EvaluationRecordService recordService;

    public AssessmentController(SpeechAssessmentService assessmentService, AssessmentProperties properties, EvaluationRecordService recordService) {
        this.assessmentService = assessmentService;
        this.properties = properties;
        this.recordService = recordService;
    }

    @PostMapping(value = "/api/speech/evaluate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReadingAssessmentResult evaluateSpeech(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "referenceText", required = false) String referenceText,
            @RequestParam(value = "sentenceId", required = false) String sentenceId,
            @RequestParam(value = "bookId", required = false) String bookId,
            @RequestParam(value = "pageId", required = false) String pageId,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        validate(file, referenceText, "file");
        return assessmentService.assess(file, referenceText.trim(), recognizedText);
    }

    @PostMapping(value = "/api/assessment/reading", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReadingAssessmentResult assessReading(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("targetText") String targetText,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        validate(audio, targetText, "audio");
        return assessmentService.assess(audio, targetText, recognizedText);
    }

    @GetMapping("/api/speech/evaluations/{id}")
    public ReadingAssessmentResult getEvaluation(@PathVariable String id) {
        return recordService.get(id);
    }

    private void validate(MultipartFile audio, String targetText, String fileFieldName) {
        if (audio == null) {
            throw new IllegalArgumentException("请使用 multipart/form-data 上传字段名为 " + fileFieldName + " 的录音文件。");
        }
        if (audio.isEmpty()) {
            throw new IllegalArgumentException("录音文件为空，请重新录音");
        }
        if (!StringUtils.hasText(targetText)) {
            throw new IllegalArgumentException("referenceText 不能为空，请传当前句子的英文原文。");
        }
        long maxBytes = Math.max(1, properties.getMaxAudioSizeMb()) * 1024L * 1024L;
        if (audio.getSize() > maxBytes) {
            throw new IllegalArgumentException("录音时间太长或文件过大，请控制在 30 秒以内。");
        }
    }
}
