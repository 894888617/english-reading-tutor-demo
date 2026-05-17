package com.demo.readingtutor.book.model;

import java.util.ArrayList;
import java.util.List;

public class BookSentence {
    private Integer index;
    private String english;
    private String chinese;
    private List<KeywordItem> keywords = new ArrayList<>();

    public BookSentence() {
    }

    public BookSentence(Integer index, String english, String chinese, List<KeywordItem> keywords) {
        this.index = index;
        this.english = english;
        this.chinese = chinese;
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
    }

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getEnglish() {
        return english;
    }

    public void setEnglish(String english) {
        this.english = english;
    }

    public String getChinese() {
        return chinese;
    }

    public void setChinese(String chinese) {
        this.chinese = chinese;
    }

    public List<KeywordItem> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<KeywordItem> keywords) {
        this.keywords = keywords == null ? new ArrayList<>() : keywords;
    }
}
