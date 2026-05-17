package com.demo.readingtutor.book.service;

import com.demo.readingtutor.book.model.Book;
import com.demo.readingtutor.book.model.BookPage;
import com.demo.readingtutor.book.model.BookSentence;
import com.demo.readingtutor.book.model.KeywordItem;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DefaultBookFactory {
    public Book create() {
        Book book = new Book();
        book.setId("default_story");
        book.setTitle("小兔子");
        book.setEnglishTitle("The Little Rabbit");
        book.setLevel("初学者");
        book.setSourceFileName("内置测试绘本");
        book.setSourceFileType("demo");
        book.setCreatedAt(LocalDateTime.now());
        book.setUpdatedAt(LocalDateTime.now());
        book.setPages(List.of(
                new BookPage(1, null, "The little rabbit is looking for his red hat. He asks the bird, have you seen my hat?", false, null, List.of(
                        new BookSentence(0, "The little rabbit is looking for his red hat.", "小兔子正在找他的红帽子。", List.of(
                                new KeywordItem("rabbit", "小兔子"),
                                new KeywordItem("red hat", "红帽子")
                        )),
                        new BookSentence(1, "He asks the bird, have you seen my hat?", "他问鸟儿：你见过我的帽子吗？", List.of(
                                new KeywordItem("bird", "鸟儿"),
                                new KeywordItem("hat", "帽子")
                        ))
                )),
                new BookPage(2, null, "The bird says, look under the tree. The rabbit finds his red hat.", false, null, List.of(
                        new BookSentence(0, "The bird says, look under the tree.", "鸟儿说：看看树下面。", List.of(
                                new KeywordItem("under the tree", "在树下面")
                        )),
                        new BookSentence(1, "The rabbit finds his red hat.", "小兔子找到了他的红帽子。", List.of(
                                new KeywordItem("finds", "找到了")
                        ))
                ))
        ));
        return book;
    }
}
