package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.SpeechEvalResult;

public interface SpeechEvalProvider {
    SpeechEvalResult evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId);
}
