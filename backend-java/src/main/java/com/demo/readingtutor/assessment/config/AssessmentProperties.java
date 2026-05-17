package com.demo.readingtutor.assessment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "assessment")
public class AssessmentProperties {
    private String provider = "mock";
    private boolean mockEnabled = true;
    private int maxAudioDurationSeconds = 30;
    private int maxAudioSizeMb = 10;
    private Vendor vendor = new Vendor();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
    public int getMaxAudioDurationSeconds() { return maxAudioDurationSeconds; }
    public void setMaxAudioDurationSeconds(int maxAudioDurationSeconds) { this.maxAudioDurationSeconds = maxAudioDurationSeconds; }
    public int getMaxAudioSizeMb() { return maxAudioSizeMb; }
    public void setMaxAudioSizeMb(int maxAudioSizeMb) { this.maxAudioSizeMb = maxAudioSizeMb; }
    public Vendor getVendor() { return vendor; }
    public void setVendor(Vendor vendor) { this.vendor = vendor; }

    public static class Vendor {
        private String appId = "";
        private String apiKey = "";
        private String apiSecret = "";
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getApiSecret() { return apiSecret; }
        public void setApiSecret(String apiSecret) { this.apiSecret = apiSecret; }
    }
}
