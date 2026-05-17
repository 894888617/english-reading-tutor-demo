package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "aliyun.dashscope")
public class RealtimeProperties {
    private String apiKey;
    private String webrtcEndpoint;
    private String realtimeModel = "qwen3.5-omni-plus-realtime";
    private String voice = "Ethan";

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getWebrtcEndpoint() {
        return webrtcEndpoint;
    }

    public void setWebrtcEndpoint(String webrtcEndpoint) {
        this.webrtcEndpoint = webrtcEndpoint;
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

    public boolean hasWebrtcEndpoint() {
        return StringUtils.hasText(webrtcEndpoint);
    }

    public String normalizedEndpointHost() {
        if (!StringUtils.hasText(webrtcEndpoint)) {
            return "";
        }
        String endpoint = webrtcEndpoint.trim();
        endpoint = endpoint.replaceFirst("^https?://", "");
        while (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        return endpoint;
    }
}
