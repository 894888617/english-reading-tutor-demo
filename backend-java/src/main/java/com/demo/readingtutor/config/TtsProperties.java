package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "tts")
public class TtsProperties {
    private String provider = "dashscope";
    private String model = "qwen3-tts-flash";
    private String defaultVoice = "Cherry";
    private String language = "English";
    private String format = "mp3";
    private double speed = 0.9;
    private double pitch = 1.0;
    private double volume = 1.0;
    private String cacheDriver = "local";
    private String cacheDir = "./storage/audio-cache";
    private int cacheTtlDays = 365;
    private List<Voice> voices = defaultVoices();

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getDefaultVoice() { return defaultVoice; }
    public void setDefaultVoice(String defaultVoice) { this.defaultVoice = defaultVoice; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public double getPitch() { return pitch; }
    public void setPitch(double pitch) { this.pitch = pitch; }
    public double getVolume() { return volume; }
    public void setVolume(double volume) { this.volume = volume; }
    public String getCacheDriver() { return cacheDriver; }
    public void setCacheDriver(String cacheDriver) { this.cacheDriver = cacheDriver; }
    public String getCacheDir() { return cacheDir; }
    public void setCacheDir(String cacheDir) { this.cacheDir = cacheDir; }
    public int getCacheTtlDays() { return cacheTtlDays; }
    public void setCacheTtlDays(int cacheTtlDays) { this.cacheTtlDays = cacheTtlDays; }
    public List<Voice> getVoices() { return voices; }
    public void setVoices(List<Voice> voices) { this.voices = voices == null || voices.isEmpty() ? defaultVoices() : voices; }

    public Voice requireVoice(String id, String model) {
        String requested = StringUtils.hasText(id) ? id : defaultVoice;
        return voices.stream()
                .filter(voice -> voice.getId().equals(requested) && voice.getModel().equals(model))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前音色不支持该 TTS 模型，请在后台配置有效 voice。"));
    }

    public static class Voice {
        private String id;
        private String name;
        private String model;
        private String language;
        private String gender;
        private String description;
        public Voice() {}
        public Voice(String id, String name, String model, String language, String gender, String description) {
            this.id = id; this.name = name; this.model = model; this.language = language; this.gender = gender; this.description = description;
        }
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getGender() { return gender; }
        public void setGender(String gender) { this.gender = gender; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    private static List<Voice> defaultVoices() {
        List<Voice> list = new ArrayList<>();
        list.add(new Voice("Cherry", "Professional Female English Teacher", "qwen3-tts-flash", "English", "female", "clear, warm, suitable for children"));
        list.add(new Voice("Ethan", "Professional Male English Teacher", "qwen3-tts-flash", "English", "male", "natural, patient, classroom style"));
        list.add(new Voice("Serena", "Warm Female English Teacher", "qwen3-tts-flash", "English", "female", "soft, friendly, young learners"));
        return list;
    }
}
