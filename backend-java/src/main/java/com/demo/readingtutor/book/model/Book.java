package com.demo.readingtutor.book.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Book {
    private String id;
    private String title;
    private String englishTitle;
    private String level;
    private String sourceFileName;
    private String sourceFileType;
    private String coverUrl;
    private List<BookPage> pages = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getEnglishTitle() { return englishTitle; }
    public void setEnglishTitle(String englishTitle) { this.englishTitle = englishTitle; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getSourceFileName() { return sourceFileName; }
    public void setSourceFileName(String sourceFileName) { this.sourceFileName = sourceFileName; }
    public String getSourceFileType() { return sourceFileType; }
    public void setSourceFileType(String sourceFileType) { this.sourceFileType = sourceFileType; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
    public List<BookPage> getPages() { return pages; }
    public void setPages(List<BookPage> pages) { this.pages = pages == null ? new ArrayList<>() : pages; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
