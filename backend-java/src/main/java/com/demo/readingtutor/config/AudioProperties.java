package com.demo.readingtutor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "audio")
public class AudioProperties {
    private String ffmpegPath = "ffmpeg";
    private int targetSampleRate = 16000;
    private int targetChannels = 1;
    private String targetFormat = "pcm";
    private String tempDir = "./storage/audio-temp";

    public String getFfmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String ffmpegPath) { this.ffmpegPath = ffmpegPath; }
    public int getTargetSampleRate() { return targetSampleRate; }
    public void setTargetSampleRate(int targetSampleRate) { this.targetSampleRate = targetSampleRate; }
    public int getTargetChannels() { return targetChannels; }
    public void setTargetChannels(int targetChannels) { this.targetChannels = targetChannels; }
    public String getTargetFormat() { return targetFormat; }
    public void setTargetFormat(String targetFormat) { this.targetFormat = targetFormat; }
    public String getTempDir() { return tempDir; }
    public void setTempDir(String tempDir) { this.tempDir = tempDir; }
}
