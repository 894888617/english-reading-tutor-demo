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
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam(value = "referenceText", required = false) String referenceText,
            @RequestParam(value = "targetText", required = false) String targetText,
            @RequestParam(value = "sentenceId", required = false) String sentenceId,
            @RequestParam(value = "bookId", required = false) String bookId,
            @RequestParam(value = "pageId", required = false) String pageId,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        MultipartFile upload = file != null ? file : audio;
        String text = StringUtils.hasText(referenceText) ? referenceText : targetText;
        validate(upload, text);
        return assessmentService.assess(upload, text, recognizedText);
    }

    @PostMapping(value = "/api/assessment/reading", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReadingAssessmentResult assessReading(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam("targetText") String targetText,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        validate(audio, targetText);
        return assessmentService.assess(audio, targetText, recognizedText);
    }

    @GetMapping("/api/speech/evaluations/{id}")
    public ReadingAssessmentResult getEvaluation(@PathVariable String id) {
        return recordService.get(id);
    }

    private void validate(MultipartFile audio, String targetText) {
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
    }
}
