package com.demo.readingtutor;

import com.demo.readingtutor.book.config.OcrProperties;
import com.demo.readingtutor.book.config.UploadProperties;
import com.demo.readingtutor.config.RealtimeProperties;
import com.demo.readingtutor.config.AiModelProperties;
import com.demo.readingtutor.config.TtsProperties;
import com.demo.readingtutor.assessment.config.AssessmentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({RealtimeProperties.class, TtsProperties.class, UploadProperties.class, OcrProperties.class, AssessmentProperties.class, AiModelProperties.class})
public class ReadingTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadingTutorApplication.class, args);
    }
}
