package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ai")
public class AiModelProperties {
    private boolean mockEnabled = false;
    private String dashscopeApiKey = "";
    private String dashscopeRegion = "beijing";
    private String dashscopeBaseUrl = "https://dashscope.aliyuncs.com/api/v1";

    public boolean isMockEnabled() { return mockEnabled; }
    public void setMockEnabled(boolean mockEnabled) { this.mockEnabled = mockEnabled; }
    public String getDashscopeApiKey() { return dashscopeApiKey; }
    public void setDashscopeApiKey(String dashscopeApiKey) { this.dashscopeApiKey = dashscopeApiKey; }
    public String getDashscopeRegion() { return dashscopeRegion; }
    public void setDashscopeRegion(String dashscopeRegion) { this.dashscopeRegion = dashscopeRegion; }
    public String getDashscopeBaseUrl() { return dashscopeBaseUrl; }
    public void setDashscopeBaseUrl(String dashscopeBaseUrl) { this.dashscopeBaseUrl = dashscopeBaseUrl; }
}
