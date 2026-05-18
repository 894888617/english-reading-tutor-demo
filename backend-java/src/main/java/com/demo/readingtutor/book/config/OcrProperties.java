package com.demo.readingtutor.book.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ocr")
public class OcrProperties {
    private String provider = "dashscope";
    private String model = "qwen-vl-ocr-2025-11-20";
    private final Baidu baidu = new Baidu();
    private final Tencent tencent = new Tencent();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Baidu getBaidu() { return baidu; }
    public Tencent getTencent() { return tencent; }

    public static class Baidu {
        private String apiKey;
        private String secretKey;
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }

    public static class Tencent {
        private String secretId;
        private String secretKey;
        public String getSecretId() { return secretId; }
        public void setSecretId(String secretId) { this.secretId = secretId; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    }
}
