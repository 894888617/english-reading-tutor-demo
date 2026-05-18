package com.demo.readingtutor.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("API request failed requestId={} path={} status={} message={}", requestId, request.getRequestURI(), ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("requestId", requestId, "message", ex.getReason() == null ? "请求处理失败。" : ex.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("Bad request requestId={} path={} message={}", requestId, request.getRequestURI(), ex.getMessage());
        return ResponseEntity.badRequest().body(Map.of("requestId", requestId, "message", ex.getMessage() == null ? "请求参数不正确。" : ex.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUpload(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.warn("Upload too large requestId={} path={}", requestId, request.getRequestURI());
        return ResponseEntity.badRequest().body(Map.of("requestId", requestId, "message", "上传失败：单个文件最大 50MB。"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex, HttpServletRequest request) {
        String requestId = requestId(request);
        log.error("Unhandled API error requestId={} path={}", requestId, request.getRequestURI(), ex);
        return ResponseEntity.internalServerError().body(Map.of("requestId", requestId, "message", "服务暂时不可用，请稍后重试。"));
    }

    private String requestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return header == null || header.isBlank() ? UUID.randomUUID().toString() : header;
    }
}
