package com.demo.readingtutor.ai.controller;

import com.demo.readingtutor.ai.providers.TtsProvider;
import com.demo.readingtutor.ai.types.AiTypes.TtsRequest;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.service.EvaluationRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@CrossOrigin(originPatterns = "*")
public class FeedbackController {
    private final EvaluationRecordService recordService;
    private final TtsProvider ttsProvider;

    public FeedbackController(EvaluationRecordService recordService, TtsProvider ttsProvider) {
        this.recordService = recordService; this.ttsProvider = ttsProvider;
    }

    @PostMapping("/speech")
    public Map<String, Object> speechFeedback(@RequestBody Map<String, String> body) {
        ReadingAssessmentResult eval = recordService.get(body.getOrDefault("evaluationId", ""));
        String text = eval.feedbackText();
        String voice = body.get("voice");
        var audio = ttsProvider.synthesize(new TtsRequest(text, "en", voice, 0.95, 1.0, 1.0, "mp3", false, null, null, "feedback-" + eval.evaluationId()));
        return Map.of("text", text, "audioUrl", audio.audioUrl());
    }
}
