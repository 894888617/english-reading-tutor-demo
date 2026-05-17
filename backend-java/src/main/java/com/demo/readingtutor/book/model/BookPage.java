package com.demo.readingtutor.book.model;

import java.util.ArrayList;
import java.util.List;

public class BookPage {
    private Integer pageNo;
    private String imageUrl;
    private String rawText;
    private Boolean needOcr = false;
    private String parseError;
    private List<BookSentence> sentences = new ArrayList<>();

    public BookPage() {
    }

    public BookPage(Integer pageNo, String imageUrl, String rawText, Boolean needOcr, String parseError, List<BookSentence> sentences) {
        this.pageNo = pageNo;
        this.imageUrl = imageUrl;
        this.rawText = rawText;
        this.needOcr = needOcr == null ? false : needOcr;
        this.parseError = parseError;
        this.sentences = sentences == null ? new ArrayList<>() : sentences;
    }

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public Boolean getNeedOcr() { return needOcr; }
    public void setNeedOcr(Boolean needOcr) { this.needOcr = needOcr; }
    public String getParseError() { return parseError; }
    public void setParseError(String parseError) { this.parseError = parseError; }
    public List<BookSentence> getSentences() { return sentences; }
    public void setSentences(List<BookSentence> sentences) { this.sentences = sentences == null ? new ArrayList<>() : sentences; }
}
