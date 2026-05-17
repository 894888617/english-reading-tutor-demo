package com.demo.readingtutor.book.service;

import java.io.File;

public interface OcrService {
    String recognizeText(File imageFile);
}
