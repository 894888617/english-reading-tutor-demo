package com.demo.readingtutor.assessment.dto;

public record PronunciationIssue(String type, String targetWord, String actualWord, Integer wordIndex, String message, String suggestion) {}
