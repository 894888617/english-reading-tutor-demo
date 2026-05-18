package com.demo.readingtutor.ai.types;

import java.util.List;
import java.util.Map;

public final class AiTypes {
    private AiTypes() {}

    public record OcrBlock(String text, Double confidence, Map<String, Object> position) {}
    public record OcrPage(int pageNo, String text, List<OcrBlock> blocks, Double confidence) {}
    public record OcrResult(List<OcrPage> pages) {}

    public record TtsRequest(
            String text,
            String language,
            String voice,
            Double speed,
            Double pitch,
            Double volume,
            String format,
            Boolean forceRefresh,
            String bookId,
            String pageId,
            String sentenceId
    ) {}

    public record TtsResult(
            String audioUrl,
            boolean cacheHit,
            Long durationMs,
            String provider,
            String model,
            String voice,
            String language,
            double speed,
            double pitch,
            double volume,
            String format
    ) {}

    public record TtsVoice(
            String id,
            String name,
            String model,
            String language,
            String gender,
            String description
    ) {}

    public record SpeechWord(
            String word,
            int score,
            boolean correct,
            Double startTime,
            Double endTime,
            List<String> phonemeErrors,
            String expectedPhoneme,
            String actualPhoneme,
            String actualWord
    ) {}

    public record SpeechEvalResult(
            int totalScore,
            int accuracyScore,
            int fluencyScore,
            int completenessScore,
            int clarityScore,
            List<SpeechWord> words,
            Object rawProviderResult
    ) {}

    public record RealtimeSession(
            String id,
            String provider,
            String model,
            String protocol,
            String endpoint,
            Map<String, Object> session
    ) {}
}
