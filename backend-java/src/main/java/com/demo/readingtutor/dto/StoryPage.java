package com.demo.readingtutor.dto;

import java.util.List;

public class StoryPage {
    private int page;
    private List<String> sentences;

    public StoryPage() {
    }

    public StoryPage(int page, List<String> sentences) {
        this.page = page;
        this.sentences = sentences;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public List<String> getSentences() {
        return sentences;
    }

    public void setSentences(List<String> sentences) {
        this.sentences = sentences;
    }
}
