package com.demo.readingtutor.controller;

import com.demo.readingtutor.dto.StoryPage;
import com.demo.readingtutor.dto.StoryResponse;
import com.demo.readingtutor.dto.StorySentence;
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
                "小兔子",
                "The Little Rabbit",
                "初学者",
                List.of(
                        new StoryPage(1, List.of(
                                new StorySentence(
                                        "The little rabbit is looking for his red hat.",
                                        "小兔子正在找他的红帽子。"
                                ),
                                new StorySentence(
                                        "He asks the bird, have you seen my hat?",
                                        "他问鸟儿：你见过我的帽子吗？"
                                )
                        )),
                        new StoryPage(2, List.of(
                                new StorySentence(
                                        "The bird says, look under the tree.",
                                        "鸟儿说：看看树下面。"
                                ),
                                new StorySentence(
                                        "The rabbit finds his red hat.",
                                        "小兔子找到了他的红帽子。"
                                )
                        ))
                )
        );
    }
}
