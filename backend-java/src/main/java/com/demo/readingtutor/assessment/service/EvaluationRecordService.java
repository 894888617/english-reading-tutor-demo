package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class EvaluationRecordService {
    private static final Path DIR = Path.of("data", "evaluations");
    private final ObjectMapper objectMapper;

    public EvaluationRecordService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; ensureDir(); }

    public ReadingAssessmentResult save(ReadingAssessmentResult result) {
        String id = result.evaluationId() == null || result.evaluationId().isBlank()
                ? "eval_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : result.evaluationId();
        ReadingAssessmentResult withId = new ReadingAssessmentResult(id, result.targetText(), result.recognizedText(), result.score(), result.wordResults(), result.issues(), result.feedbackText(), result.feedbackAudioUrl());
        try {
            ensureDir();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(DIR.resolve(id + ".json").toFile(), withId);
            return withId;
        } catch (IOException ex) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "保存评分记录失败。", ex);
        }
    }

    public ReadingAssessmentResult get(String id) {
        Path path = DIR.resolve(id + ".json");
        if (!Files.exists(path)) throw new ResponseStatusException(NOT_FOUND, "评分记录不存在。");
        try { return objectMapper.readValue(path.toFile(), ReadingAssessmentResult.class); }
        catch (IOException ex) { throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "读取评分记录失败。", ex); }
    }

    private void ensureDir() {
        try { Files.createDirectories(DIR); } catch (IOException ex) { throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "初始化评分存储失败。", ex); }
    }
}
