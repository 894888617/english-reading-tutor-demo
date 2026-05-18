package com.demo.readingtutor.assessment.dto;

import java.util.List;

public record ReadingAssessmentResult(
        String evaluationId,
        String targetText,
        String recognizedText,
        ReadingScore score,
        List<WordToken> wordResults,
        List<PronunciationIssue> issues,
        String feedbackText,
        String feedbackAudioUrl,
        String recordingUrl,
        Boolean pcmGenerated
) {}
