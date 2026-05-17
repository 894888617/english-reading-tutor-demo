package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "aliyun.dashscope")
public class RealtimeProperties {
    private String apiKey;
    private String realtimeWsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";
    private String realtimeModel = "qwen3.5-omni-plus-realtime";
    private String voice = "Tina";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getRealtimeWsUrl() {
        return realtimeWsUrl;
    }

    public void setRealtimeWsUrl(String realtimeWsUrl) {
        this.realtimeWsUrl = realtimeWsUrl;
    }

    public String getRealtimeModel() {
        return realtimeModel;
    }

    public void setRealtimeModel(String realtimeModel) {
        this.realtimeModel = realtimeModel;
    }

    public String getVoice() {
        return voice;
    }

    public void setVoice(String voice) {
        this.voice = voice;
    }

    public boolean hasApiKey() {
        return StringUtils.hasText(apiKey);
    }
}
