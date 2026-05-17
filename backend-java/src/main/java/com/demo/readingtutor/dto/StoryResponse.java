package com.demo.readingtutor.dto;

import java.util.List;

public class StoryResponse {
    private String title;
    private String englishTitle;
    private String level;
    private List<StoryPage> pages;

    public StoryResponse() {
    }

    public StoryResponse(String title, String englishTitle, String level, List<StoryPage> pages) {
        this.title = title;
        this.englishTitle = englishTitle;
        this.level = level;
        this.pages = pages;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getEnglishTitle() {
        return englishTitle;
    }

    public void setEnglishTitle(String englishTitle) {
        this.englishTitle = englishTitle;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public List<StoryPage> getPages() {
        return pages;
    }

    public void setPages(List<StoryPage> pages) {
        this.pages = pages;
    }
}
