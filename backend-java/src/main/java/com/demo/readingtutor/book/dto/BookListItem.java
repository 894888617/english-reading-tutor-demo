package com.demo.readingtutor.book.dto;

import java.time.LocalDateTime;

public record BookListItem(
        String id,
        String title,
        String englishTitle,
        String level,
        int pageCount,
        LocalDateTime createdAt
) {
}
