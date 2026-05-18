package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "aliyun.dashscope")
public class RealtimeProperties {
    private String apiKey;
    private String realtimeWsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
    private String realtimeModel = "qwen3.5-omni-plus-realtime";
    private String defaultVoice = "Jennifer";
    private String protocol = "webrtc";
    private boolean vadEnabled = true;
    private boolean interruptEnabled = true;
    private String liveTranslateModel = "qwen3-livetranslate-flash-realtime";
    private String liveTranslateSourceLanguage = "en";
    private String liveTranslateTargetLanguage = "zh";
    private String liveTranslateVoice = "Cherry";

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getRealtimeWsUrl() { return realtimeWsUrl; }
    public void setRealtimeWsUrl(String realtimeWsUrl) { this.realtimeWsUrl = realtimeWsUrl; }
    public String getRealtimeModel() { return realtimeModel; }
    public void setRealtimeModel(String realtimeModel) { this.realtimeModel = realtimeModel; }
    public String getDefaultVoice() { return defaultVoice; }
    public void setDefaultVoice(String defaultVoice) { this.defaultVoice = defaultVoice; }
    public String getProtocol() { return protocol; }
    public void setProtocol(String protocol) { this.protocol = protocol; }
    public boolean isVadEnabled() { return vadEnabled; }
    public void setVadEnabled(boolean vadEnabled) { this.vadEnabled = vadEnabled; }
    public boolean isInterruptEnabled() { return interruptEnabled; }
    public void setInterruptEnabled(boolean interruptEnabled) { this.interruptEnabled = interruptEnabled; }
    public String getLiveTranslateModel() { return liveTranslateModel; }
    public void setLiveTranslateModel(String liveTranslateModel) { this.liveTranslateModel = liveTranslateModel; }
    public String getLiveTranslateSourceLanguage() { return liveTranslateSourceLanguage; }
    public void setLiveTranslateSourceLanguage(String liveTranslateSourceLanguage) { this.liveTranslateSourceLanguage = liveTranslateSourceLanguage; }
    public String getLiveTranslateTargetLanguage() { return liveTranslateTargetLanguage; }
    public void setLiveTranslateTargetLanguage(String liveTranslateTargetLanguage) { this.liveTranslateTargetLanguage = liveTranslateTargetLanguage; }
    public String getLiveTranslateVoice() { return liveTranslateVoice; }
    public void setLiveTranslateVoice(String liveTranslateVoice) { this.liveTranslateVoice = liveTranslateVoice; }

    public boolean hasApiKey() { return StringUtils.hasText(apiKey); }
    public String defaultTutorInstructions() {
        return "You are a professional English tutor for primary school students. Keep replies short, encouraging, and focused on the current sentence or word. Do not give long explanations.";
    }
}
