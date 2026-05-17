package com.demo.readingtutor.book.model;

public class KeywordItem {
    private String word;
    private String meaning;

    public KeywordItem() {
    }

    public KeywordItem(String word, String meaning) {
        this.word = word;
        this.meaning = meaning;
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getMeaning() {
        return meaning;
    }

    public void setMeaning(String meaning) {
        this.meaning = meaning;
    }
}
