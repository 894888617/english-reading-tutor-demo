package com.demo.readingtutor.dto;

import java.util.List;

public class StoryPage {
    private int page;
    private List<StorySentence> sentences;

    public StoryPage() {
    }

    public StoryPage(int page, List<StorySentence> sentences) {
        this.page = page;
        this.sentences = sentences;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public List<StorySentence> getSentences() {
        return sentences;
    }

    public void setSentences(List<StorySentence> sentences) {
        this.sentences = sentences;
    }
}
