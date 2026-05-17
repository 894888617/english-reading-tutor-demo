package com.demo.readingtutor;

import com.demo.readingtutor.config.RealtimeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RealtimeProperties.class)
public class ReadingTutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReadingTutorApplication.class, args);
    }
}
