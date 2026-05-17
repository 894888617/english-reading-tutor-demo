package com.demo.readingtutor.controller;

import com.demo.readingtutor.dto.StoryPage;
import com.demo.readingtutor.dto.StoryResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/story")
@CrossOrigin(origins = "http://localhost:5173")
public class StoryController {

    @GetMapping
    public StoryResponse getStory() {
        return new StoryResponse(
                "The Little Rabbit",
                "Beginner",
                List.of(
                        new StoryPage(1, List.of(
                                "The little rabbit is looking for his red hat.",
                                "He asks the bird, have you seen my hat?"
                        )),
                        new StoryPage(2, List.of(
                                "The bird says, look under the tree.",
                                "The rabbit finds his red hat."
                        ))
                )
        );
    }
}
