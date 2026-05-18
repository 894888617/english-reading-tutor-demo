package com.demo.readingtutor.assessment.controller;

import com.demo.readingtutor.assessment.config.AssessmentProperties;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.dto.WordToken;
import com.demo.readingtutor.assessment.service.EvaluationRecordService;
import com.demo.readingtutor.assessment.service.SpeechAssessmentService;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public Map<String, Object> evaluateSpeech(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "referenceText", required = false) String referenceText,
            @RequestParam(value = "sentenceId", required = false) String sentenceId,
            @RequestParam(value = "bookId", required = false) String bookId,
            @RequestParam(value = "pageId", required = false) String pageId,
            @RequestParam(value = "recognizedText", required = false) String recognizedText
    ) {
        validate(file, referenceText, "file");
        ReadingAssessmentResult result = assessmentService.assess(file, referenceText.trim(), recognizedText);
        Map<String, Object> data = toSpeechEvaluationData(result);
        return Map.of("success", true, "data", data);
    }

    private Map<String, Object> toSpeechEvaluationData(ReadingAssessmentResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("evaluationId", result.evaluationId());
        data.put("totalScore", result.score().totalScore());
        data.put("accuracyScore", result.score().accuracyScore());
        data.put("fluencyScore", result.score().fluencyScore());
        data.put("completenessScore", result.score().completenessScore());
        data.put("clarityScore", result.score().clarityScore());
        List<Map<String, Object>> words = result.wordResults().stream().map(this::toWordData).toList();
        data.put("words", words);
        data.put("targetText", result.targetText());
        data.put("recognizedText", result.recognizedText());
        data.put("score", result.score());
        data.put("wordResults", result.wordResults());
        data.put("issues", result.issues());
        data.put("feedbackText", result.feedbackText());
        data.put("feedbackAudioUrl", result.feedbackAudioUrl());
        data.put("recordingUrl", result.recordingUrl());
        data.put("pcmGenerated", result.pcmGenerated());
        return data;
    }

    private Map<String, Object> toWordData(WordToken token) {
        Map<String, Object> word = new LinkedHashMap<>();
        word.put("index", token.index());
        word.put("word", token.text());
        word.put("text", token.text());
        word.put("normalized", token.normalized());
        word.put("status", token.status());
        word.put("meaning", token.meaning());
        return word;
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
