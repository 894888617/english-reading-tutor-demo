package com.demo.readingtutor.book.service;

import com.demo.readingtutor.book.config.UploadProperties;
import com.demo.readingtutor.book.model.BookPage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class PdfBookParser {
    private final UploadProperties uploadProperties;
    private final BookTextSplitter textSplitter;

    public PdfBookParser(UploadProperties uploadProperties, BookTextSplitter textSplitter) {
        this.uploadProperties = uploadProperties;
        this.textSplitter = textSplitter;
    }

    public List<BookPage> parse(File pdfFile) {
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            int pageCount = document.getNumberOfPages();
            if (pageCount > uploadProperties.getMaxPdfPages()) {
                throw new ResponseStatusException(BAD_REQUEST, "上传失败：PDF 页数超过 " + uploadProperties.getMaxPdfPages() + " 页，请拆分后再上传。");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            List<BookPage> pages = new ArrayList<>();
            for (int i = 1; i <= pageCount; i++) {
                try {
                    stripper.setStartPage(i);
                    stripper.setEndPage(i);
                    String text = normalize(stripper.getText(document));
                    boolean needOcr = !StringUtils.hasText(text);
                    pages.add(new BookPage(
                            i,
                            null,
                            needOcr ? "" : text,
                            needOcr,
                            needOcr ? "解析失败：没有识别到英文文本，请手动输入本页内容。" : null,
                            needOcr ? List.of() : textSplitter.split(text)
                    ));
                } catch (Exception pageError) {
                    pages.add(new BookPage(
                            i,
                            null,
                            "",
                            true,
                            "第 " + i + " 页解析失败：" + pageError.getMessage() + "。请手动补录文本。",
                            List.of()
                    ));
                }
            }
            return pages;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(BAD_REQUEST, "PDF 解析失败：无法读取该文件，请确认文件未损坏。", ex);
        }
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace('\r', ' ').replaceAll("[ \\t]*\\n[ \\t]*", " ").replaceAll("\\s+", " ").trim();
    }
}
