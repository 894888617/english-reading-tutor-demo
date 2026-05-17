package com.demo.readingtutor;

import com.demo.readingtutor.book.config.OcrProperties;
import com.demo.readingtutor.book.config.UploadProperties;
import com.demo.readingtutor.config.RealtimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RealtimeProperties.class, UploadProperties.class, OcrProperties.class})
public class ReadingTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadingTutorApplication.class, args);
    }
}
