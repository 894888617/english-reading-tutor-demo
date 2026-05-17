package com.demo.readingtutor.book.service;

import com.demo.readingtutor.book.config.UploadProperties;
import com.demo.readingtutor.book.dto.BookListItem;
import com.demo.readingtutor.book.model.Book;
import com.demo.readingtutor.book.model.BookPage;
import com.demo.readingtutor.book.model.BookSentence;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class BookService {
    private static final Path DATA_DIR = Path.of("data");
    private static final Path UPLOAD_DIR = DATA_DIR.resolve("uploads");
    private static final Path BOOK_DIR = DATA_DIR.resolve("books");
    private static final Path COVER_DIR = DATA_DIR.resolve("covers");

    private final ObjectMapper objectMapper;
    private final UploadProperties uploadProperties;
    private final PdfBookParser pdfBookParser;
    private final OcrService ocrService;
    private final BookTextSplitter textSplitter;
    private final DefaultBookFactory defaultBookFactory;

    public BookService(ObjectMapper objectMapper, UploadProperties uploadProperties, PdfBookParser pdfBookParser,
                       OcrService ocrService, BookTextSplitter textSplitter, DefaultBookFactory defaultBookFactory) {
        this.objectMapper = objectMapper;
        this.uploadProperties = uploadProperties;
        this.pdfBookParser = pdfBookParser;
        this.ocrService = ocrService;
        this.textSplitter = textSplitter;
        this.defaultBookFactory = defaultBookFactory;
        ensureDataDirs();
    }

    public Book upload(MultipartFile file, String title, String englishTitle, String level) {
        validateUpload(file);
        String originalName = cleanFileName(file.getOriginalFilename());
        String extension = extensionOf(originalName);
        String bookId = "book_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String storedName = bookId + "." + extension;
        Path target = UPLOAD_DIR.resolve(storedName);

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "保存绘本失败：无法保存上传文件。", ex);
        }

        List<BookPage> pages = parsePages(target, extension, storedName);
        LocalDateTime now = LocalDateTime.now();
        Book book = new Book();
        book.setId(bookId);
        book.setTitle(StringUtils.hasText(title) ? title.trim() : removeExtension(originalName));
        book.setEnglishTitle(StringUtils.hasText(englishTitle) ? englishTitle.trim() : removeExtension(originalName));
        book.setLevel(StringUtils.hasText(level) ? level.trim() : "初学者");
        book.setSourceFileName(storedName);
        book.setSourceFileType(extension);
        book.setPages(pages);
        book.setCreatedAt(now);
        book.setUpdatedAt(now);
        save(book);
        return book;
    }

    public List<BookListItem> list() {
        try {
            if (!Files.exists(BOOK_DIR)) {
                return List.of();
            }
            List<BookListItem> uploaded = Files.list(BOOK_DIR)
                    .filter(path -> path.getFileName().toString().startsWith("book-") && path.getFileName().toString().endsWith(".json"))
                    .map(this::readBookPathQuietly)
                    .filter(book -> book != null)
                    .sorted(Comparator.comparing(Book::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                    .map(book -> new BookListItem(book.getId(), book.getTitle(), book.getEnglishTitle(), book.getLevel(), book.getPages().size(), book.getCreatedAt()))
                    .toList();
            if (uploaded.isEmpty()) {
                Book defaultBook = defaultBookFactory.create();
                return List.of(new BookListItem(defaultBook.getId(), defaultBook.getTitle(), defaultBook.getEnglishTitle(), defaultBook.getLevel(), defaultBook.getPages().size(), defaultBook.getCreatedAt()));
            }
            return uploaded;
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "读取绘本失败：无法读取绘本列表。", ex);
        }
    }

    public Book get(String bookId) {
        if ("default_story".equals(bookId)) {
            return defaultBookFactory.create();
        }
        Path path = bookPath(bookId);
        if (!Files.exists(path)) {
            throw new ResponseStatusException(NOT_FOUND, "读取绘本失败：绘本不存在。");
        }
        try {
            return objectMapper.readValue(path.toFile(), Book.class);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "读取绘本失败：绘本文件无法解析。", ex);
        }
    }

    public Book update(String bookId, Book book) {
        if (!StringUtils.hasText(bookId) || "default_story".equals(bookId)) {
            throw new ResponseStatusException(BAD_REQUEST, "保存绘本失败：内置测试绘本不能覆盖，请先上传新绘本。");
        }
        Book existing = get(bookId);
        book.setId(bookId);
        book.setSourceFileName(existing.getSourceFileName());
        book.setSourceFileType(existing.getSourceFileType());
        book.setCoverUrl(existing.getCoverUrl());
        book.setCreatedAt(existing.getCreatedAt());
        book.setUpdatedAt(LocalDateTime.now());
        normalizeBook(book);
        save(book);
        return book;
    }

    public void delete(String bookId) {
        if (!StringUtils.hasText(bookId) || "default_story".equals(bookId)) {
            throw new ResponseStatusException(BAD_REQUEST, "删除失败：内置测试绘本不能删除。");
        }
        Book book = get(bookId);
        try {
            Files.deleteIfExists(bookPath(bookId));
            if (StringUtils.hasText(book.getSourceFileName())) {
                Files.deleteIfExists(UPLOAD_DIR.resolve(book.getSourceFileName()));
            }
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "删除绘本失败：无法删除本地文件。", ex);
        }
    }

    private List<BookPage> parsePages(Path target, String extension, String storedName) {
        if ("pdf".equals(extension)) {
            List<BookPage> pages = pdfBookParser.parse(target.toFile());
            if (pages.stream().allMatch(page -> !StringUtils.hasText(page.getRawText()) && page.getSentences().isEmpty())) {
                pages.forEach(page -> page.setNeedOcr(true));
            }
            return pages;
        }
        String ocrText = ocrService.recognizeText(target.toFile());
        BookPage page = new BookPage(1, "/uploads/" + storedName, ocrText, true, ocrText, textSplitter.split(ocrText));
        return List.of(page);
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "上传失败：文件为空，请选择 PDF、JPG 或 PNG 文件。");
        }
        long maxBytes = uploadProperties.getMaxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(BAD_REQUEST, "上传失败：单个文件最大 " + uploadProperties.getMaxFileSizeMb() + "MB。");
        }
        String extension = extensionOf(cleanFileName(file.getOriginalFilename()));
        if (!List.of("pdf", "jpg", "jpeg", "png").contains(extension)) {
            throw new ResponseStatusException(BAD_REQUEST, "上传失败：当前文件格式不支持，请上传 PDF、JPG 或 PNG。");
        }
    }

    private void normalizeBook(Book book) {
        if (!StringUtils.hasText(book.getTitle())) {
            book.setTitle("未命名绘本");
        }
        if (!StringUtils.hasText(book.getEnglishTitle())) {
            book.setEnglishTitle(book.getTitle());
        }
        if (!StringUtils.hasText(book.getLevel())) {
            book.setLevel("初学者");
        }
        for (int pageIndex = 0; pageIndex < book.getPages().size(); pageIndex++) {
            BookPage page = book.getPages().get(pageIndex);
            page.setPageNo(page.getPageNo() == null ? pageIndex + 1 : page.getPageNo());
            for (int sentenceIndex = 0; sentenceIndex < page.getSentences().size(); sentenceIndex++) {
                BookSentence sentence = page.getSentences().get(sentenceIndex);
                sentence.setIndex(sentenceIndex);
                if (sentence.getEnglish() == null) {
                    sentence.setEnglish("");
                }
                if (sentence.getChinese() == null) {
                    sentence.setChinese("");
                }
            }
        }
    }

    private void save(Book book) {
        try {
            ensureDataDirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(bookPath(book.getId()).toFile(), book);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "保存绘本失败：无法写入本地 JSON 文件。", ex);
        }
    }

    private Book readBookPathQuietly(Path path) {
        try {
            return objectMapper.readValue(path.toFile(), Book.class);
        } catch (IOException ex) {
            return null;
        }
    }

    private Path bookPath(String bookId) {
        return BOOK_DIR.resolve("book-" + bookId + ".json");
    }

    private void ensureDataDirs() {
        try {
            Files.createDirectories(UPLOAD_DIR);
            Files.createDirectories(BOOK_DIR);
            Files.createDirectories(COVER_DIR);
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "初始化本地存储失败：无法创建 data 目录。", ex);
        }
    }

    private String cleanFileName(String filename) {
        String value = StringUtils.hasText(filename) ? filename : "book.pdf";
        return Path.of(value).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String extensionOf(String filename) {
        int index = filename == null ? -1 : filename.lastIndexOf('.');
        return index >= 0 ? filename.substring(index + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String removeExtension(String filename) {
        int index = filename == null ? -1 : filename.lastIndexOf('.');
        return index > 0 ? filename.substring(0, index) : (StringUtils.hasText(filename) ? filename : "未命名绘本");
    }
}
