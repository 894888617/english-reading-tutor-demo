package com.demo.readingtutor.book.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {
    private int maxFileSizeMb = 20;
    private int maxPdfPages = 30;

    public int getMaxFileSizeMb() { return maxFileSizeMb; }
    public void setMaxFileSizeMb(int maxFileSizeMb) { this.maxFileSizeMb = maxFileSizeMb; }
    public int getMaxPdfPages() { return maxPdfPages; }
    public void setMaxPdfPages(int maxPdfPages) { this.maxPdfPages = maxPdfPages; }
}
