package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.assessment.dto.PronunciationIssue;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.dto.ReadingScore;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@Primary
public class MockSpeechAssessmentService implements SpeechAssessmentService {
    private final PronunciationDiffService diffService;

    public MockSpeechAssessmentService(PronunciationDiffService diffService) {
        this.diffService = diffService;
    }

    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText) {
        return assess(audio, targetText, targetText);
    }

    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText, String recognizedText) {
        String safeTarget = StringUtils.hasText(targetText) ? targetText.trim() : "";
        String safeRecognized = StringUtils.hasText(recognizedText) ? recognizedText.trim() : safeTarget;
        PronunciationDiffService.DiffResult diff = diffService.diff(safeTarget, safeRecognized);
        int issueCount = diff.issues().size();
        long missed = diff.issues().stream().filter(issue -> "missed".equals(issue.type())).count();
        long wrong = diff.issues().stream().filter(issue -> "wrong".equals(issue.type())).count();
        long extra = diff.issues().stream().filter(issue -> "extra".equals(issue.type())).count();
        int wordCount = Math.max(1, diff.targetWordCount());

        int accuracy = clamp(96 - (int) Math.round((wrong * 18 + missed * 14 + extra * 8) * 1.0 / wordCount));
        int completeness = clamp(96 - (int) Math.round(missed * 22.0 / wordCount));
        int fluency = clamp(issueCount == 0 ? 90 : 86 - issueCount * 4);
        int clarity = clamp(issueCount == 0 ? 92 : 88 - (int) wrong * 7);
        int total = (int) Math.round(accuracy * 0.35 + fluency * 0.25 + completeness * 0.25 + clarity * 0.15);
        ReadingScore score = new ReadingScore(total, accuracy, fluency, completeness, clarity);
        return new ReadingAssessmentResult(safeTarget, safeRecognized, score, diff.wordResults(), diff.issues(), feedback(score, diff.issues()));
    }

    private String feedback(ReadingScore score, List<PronunciationIssue> issues) {
        if (issues.isEmpty()) {
            return "读得很好，整体很完整。可以继续保持自然语速。";
        }
        StringBuilder builder = new StringBuilder("你读得不错，大部分单词都读出来了。");
        builder.append("有两个小地方可以再练：");
        issues.stream().limit(2).forEach(issue -> builder.append(issue.message()).append("。"));
        builder.append("请先听标准发音，再慢慢跟读一遍。");
        return builder.toString();
    }

    private int clamp(int value) {
        return Math.max(45, Math.min(100, value));
    }
}
