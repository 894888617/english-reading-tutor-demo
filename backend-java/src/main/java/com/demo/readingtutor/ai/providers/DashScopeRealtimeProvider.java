package com.demo.readingtutor.ai.providers;

import com.demo.readingtutor.ai.types.AiTypes.RealtimeSession;
import com.demo.readingtutor.config.AiModelProperties;
import com.demo.readingtutor.config.RealtimeProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@Service
public class DashScopeRealtimeProvider implements RealtimeSessionProviders {
    private final AiModelProperties ai;
    private final RealtimeProperties realtime;

    public DashScopeRealtimeProvider(AiModelProperties ai, RealtimeProperties realtime) { this.ai = ai; this.realtime = realtime; }

    @Override
    public RealtimeSession createLiveTranslateSession(String sourceLanguage, String targetLanguage, String voice) {
        requireKey();
        return new RealtimeSession("lt_" + UUID.randomUUID().toString().replace("-", ""), "dashscope", realtime.getLiveTranslateModel(), "webrtc", realtime.getRealtimeWsUrl(), Map.of(
                "source_language", sourceLanguage,
                "target_language", targetLanguage,
                "voice", StringUtils.hasText(voice) ? voice : realtime.getLiveTranslateVoice(),
                "input_audio_format", "pcm",
                "output_audio_format", "pcm"
        ));
    }

    @Override
    public RealtimeSession createTutorWebRtcSession(String userId, String voice, String instructions, boolean vadEnabled, boolean interruptEnabled) {
        requireKey();
        return new RealtimeSession("tutor_" + UUID.randomUUID().toString().replace("-", ""), "dashscope", realtime.getRealtimeModel(), realtime.getProtocol(), realtime.getRealtimeWsUrl(), Map.of(
                "user_id", userId,
                "scenario", "english_tutor",
                "voice", StringUtils.hasText(voice) ? voice : realtime.getDefaultVoice(),
                "instructions", StringUtils.hasText(instructions) ? instructions : realtime.defaultTutorInstructions(),
                "vad_enabled", vadEnabled,
                "interrupt_enabled", interruptEnabled,
                "cancel_event", "response.cancel"
        ));
    }

    private void requireKey() {
        if (!StringUtils.hasText(ai.getDashscopeApiKey())) throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "DashScope API Key 未配置");
    }
}
