package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import org.springframework.web.multipart.MultipartFile;

public interface SpeechAssessmentService {
    ReadingAssessmentResult assess(MultipartFile audio, String targetText);
    ReadingAssessmentResult assess(MultipartFile audio, String targetText, String recognizedText);
}
