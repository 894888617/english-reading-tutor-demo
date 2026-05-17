package com.demo.readingtutor.book.service;

import com.demo.readingtutor.book.model.BookSentence;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class BookTextSplitter {
    public List<BookSentence> split(String rawText) {
        List<BookSentence> sentences = new ArrayList<>();
        if (!StringUtils.hasText(rawText)) {
            return sentences;
        }

        String normalized = rawText.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            current.append(ch);
            if (ch == '\'') {
                inSingleQuote = !inSingleQuote;
            } else if (ch == '"' || ch == '“' || ch == '”') {
                inDoubleQuote = !inDoubleQuote;
            }

            boolean boundary = (ch == '.' || ch == '?' || ch == '!') && !inSingleQuote && !inDoubleQuote;
            if (boundary) {
                addSentence(sentences, current.toString());
                current.setLength(0);
            }
        }
        addSentence(sentences, current.toString());

        for (int i = 0; i < sentences.size(); i++) {
            sentences.get(i).setIndex(i);
        }
        return sentences;
    }

    private void addSentence(List<BookSentence> sentences, String text) {
        String sentence = text == null ? "" : text.trim();
        if (sentence.length() < 3 || !sentence.matches(".*[A-Za-z].*")) {
            return;
        }
        sentences.add(new BookSentence(sentences.size(), sentence, "", List.of()));
    }
}
