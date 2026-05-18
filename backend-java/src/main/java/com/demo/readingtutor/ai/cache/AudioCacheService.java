package com.demo.readingtutor.ai.cache;

import com.demo.readingtutor.config.TtsProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class AudioCacheService {
    private final TtsProperties properties;

    public AudioCacheService(TtsProperties properties) {
        this.properties = properties;
        ensureDir();
    }

    public CacheKey buildKey(String model, String voice, String language, double speed, double pitch, double volume, String format, String text) {
        String textHash = DigestUtils.md5DigestAsHex(text.getBytes(StandardCharsets.UTF_8));
        String raw = String.join(":", "tts", model, voice, language, normalize(speed), normalize(pitch), normalize(volume), format, textHash);
        String fileName = DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)) + "." + format;
        return new CacheKey(raw, textHash, path().resolve(fileName), "/audio-cache/" + fileName);
    }

    public Optional<String> find(CacheKey key) {
        return Files.exists(key.path()) && isNonEmptyFile(key.path()) ? Optional.of(key.audioUrl()) : Optional.empty();
    }

    public String put(CacheKey key, byte[] bytes) {
        try {
            ensureDir();
            if (bytes == null || bytes.length == 0) {
                throw new IllegalArgumentException("音频缓存内容不能为空。");
            }
            Files.write(key.path(), bytes);
            return key.audioUrl();
        } catch (IOException ex) {
            throw new IllegalStateException("保存朗读音频缓存失败。", ex);
        }
    }

    public long fileSize(CacheKey key) {
        try { return Files.size(key.path()); } catch (IOException ex) { return 0L; }
    }

    public int clear() {
        try {
            ensureDir();
            try (var stream = Files.list(path())) {
                return (int) stream.filter(Files::isRegularFile).map(file -> {
                    try { Files.deleteIfExists(file); return 1; } catch (IOException ex) { return 0; }
                }).mapToInt(Integer::intValue).sum();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("清空音频缓存失败。", ex);
        }
    }

    private void ensureDir() {
        try { Files.createDirectories(path()); } catch (IOException ex) { throw new IllegalStateException("初始化音频缓存目录失败。", ex); }
    }

    private boolean isNonEmptyFile(Path path) {
        try { return Files.size(path) > 0; } catch (IOException ex) { return false; }
    }

    private Path path() { return Path.of(properties.getCacheDir()).toAbsolutePath().normalize(); }
    private String normalize(double value) { return String.format(java.util.Locale.ROOT, "%.2f", value); }

    public record CacheKey(String rawKey, String textHash, Path path, String audioUrl) {}
}
