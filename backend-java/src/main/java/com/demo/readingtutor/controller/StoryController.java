package com.demo.readingtutor.controller;

import com.demo.readingtutor.book.model.Book;
import com.demo.readingtutor.book.service.DefaultBookFactory;
import com.demo.readingtutor.dto.StoryPage;
import com.demo.readingtutor.dto.StoryResponse;
import com.demo.readingtutor.dto.StorySentence;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/story")
@CrossOrigin(originPatterns = "*")
public class StoryController {
    private final DefaultBookFactory defaultBookFactory;

    public StoryController(DefaultBookFactory defaultBookFactory) {
        this.defaultBookFactory = defaultBookFactory;
    }

    @GetMapping
    public StoryResponse getStory() {
        Book book = defaultBookFactory.create();
        return new StoryResponse(
                book.getTitle(),
                book.getEnglishTitle(),
                book.getLevel(),
                book.getPages().stream()
                        .map(page -> new StoryPage(
                                page.getPageNo(),
                                page.getSentences().stream()
                                        .map(sentence -> new StorySentence(sentence.getEnglish(), sentence.getChinese()))
                                        .toList()
                        ))
                        .toList()
        );
    }
}
