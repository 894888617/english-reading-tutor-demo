package com.demo.readingtutor.ai;

import com.demo.readingtutor.ai.providers.OcrProvider;
import com.demo.readingtutor.ai.providers.RealtimeSessionProviders;
import com.demo.readingtutor.ai.providers.SpeechEvalProvider;
import com.demo.readingtutor.ai.providers.TtsProvider;
public class AiServices {
    private final OcrProvider ocrProvider;
    private final TtsProvider ttsProvider;
    private final SpeechEvalProvider speechEvalProvider;
    private final RealtimeSessionProviders realtimeSessionProviders;

    public AiServices(OcrProvider ocrProvider, TtsProvider ttsProvider, SpeechEvalProvider speechEvalProvider, RealtimeSessionProviders realtimeSessionProviders) {
        this.ocrProvider = ocrProvider;
        this.ttsProvider = ttsProvider;
        this.speechEvalProvider = speechEvalProvider;
        this.realtimeSessionProviders = realtimeSessionProviders;
    }

    public OcrProvider ocrProvider() { return ocrProvider; }
    public TtsProvider ttsProvider() { return ttsProvider; }
    public SpeechEvalProvider speechEvalProvider() { return speechEvalProvider; }
    public RealtimeSessionProviders realtimeSessionProviders() { return realtimeSessionProviders; }
}
