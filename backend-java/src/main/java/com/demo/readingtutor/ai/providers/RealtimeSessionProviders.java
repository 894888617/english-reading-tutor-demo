package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.RealtimeSession;

public interface RealtimeSessionProviders {
    RealtimeSession createLiveTranslateSession(String sourceLanguage, String targetLanguage, String voice);
    RealtimeSession createTutorWebRtcSession(String userId, String voice, String instructions, boolean vadEnabled, boolean interruptEnabled);
}
