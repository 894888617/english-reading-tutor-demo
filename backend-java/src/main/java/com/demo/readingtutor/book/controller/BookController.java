package com.demo.readingtutor.book.controller;

import com.demo.readingtutor.book.dto.BookListItem;
import com.demo.readingtutor.book.model.Book;
import com.demo.readingtutor.book.service.BookService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/books")
@CrossOrigin(origins = "http://localhost:5173")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @PostMapping("/upload")
    public Book upload(@RequestParam("file") MultipartFile file,
                       @RequestParam(value = "title", required = false) String title,
                       @RequestParam(value = "englishTitle", required = false) String englishTitle,
                       @RequestParam(value = "level", required = false) String level) {
        return bookService.upload(file, title, englishTitle, level);
    }

    @GetMapping
    public List<BookListItem> list() {
        return bookService.list();
    }

    @GetMapping("/{bookId}")
    public Book get(@PathVariable String bookId) {
        return bookService.get(bookId);
    }

    @PutMapping("/{bookId}")
    public Book update(@PathVariable String bookId, @RequestBody Book book) {
        return bookService.update(bookId, book);
    }

    @DeleteMapping("/{bookId}")
    public Map<String, Boolean> delete(@PathVariable String bookId) {
        bookService.delete(bookId);
        return Map.of("success", true);
    }
}
