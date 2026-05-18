package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.SpeechEvalResult;

public interface SpeechEvalProvider {
    default SpeechEvalResult evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId) {
        return evaluate(audioFile, referenceText, language, sentenceId, userId, "application/octet-stream", "audio.pcm");
    }

    SpeechEvalResult evaluate(byte[] audioFile, String referenceText, String language, String sentenceId, String userId, String contentType, String originalFilename);
}
