package com.demo.readingtutor.assessment.audio;

import com.demo.readingtutor.config.AudioProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AudioTranscodeService {
    private static final Logger log = LoggerFactory.getLogger(AudioTranscodeService.class);
    private final AudioProperties properties;

    public AudioTranscodeService(AudioProperties properties) {
        this.properties = properties;
    }

    public byte[] toPcm16kMono(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录音文件为空，请重新录音");
        }
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long originalSize = file.getSize();
        log.info("[SpeechEval] originalFilename={}", originalFilename);
        log.info("[SpeechEval] contentType={}", contentType);
        log.info("[SpeechEval] originalSize={}", originalSize);

        Path tempDir = Path.of(properties.getTempDir()).toAbsolutePath().normalize();
        Path input = null;
        Path output = null;
        try {
            Files.createDirectories(tempDir);
            String suffix = detectSuffix(originalFilename, contentType);
            String id = UUID.randomUUID().toString();
            input = tempDir.resolve("speech-eval-input-" + id + suffix);
            output = tempDir.resolve("speech-eval-output-" + id + ".pcm");
            file.transferTo(input);

            ProcessBuilder pb = new ProcessBuilder(
                    properties.getFfmpegPath(),
                    "-y",
                    "-i", input.toString(),
                    "-vn",
                    "-ac", String.valueOf(properties.getTargetChannels()),
                    "-ar", String.valueOf(properties.getTargetSampleRate()),
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    output.toString()
            );
            pb.redirectErrorStream(true);
            Process process;
            try {
                process = pb.start();
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "ffmpeg 未安装或不可执行，请安装 ffmpeg，或通过 FFMPEG_PATH 指向可执行文件。", ex);
            }
            String ffmpegOutput = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频转码超时，请缩短录音后重试。");
            }
            int exitCode = process.exitValue();
            log.info("[SpeechEval] ffmpegExitCode={}", exitCode);
            if (exitCode != 0) {
                log.warn("[SpeechEval] ffmpeg failed output={}", abbreviate(ffmpegOutput));
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频转码失败，请确认上传的是 webm/mp3/wav/m4a/ogg/mp4 等浏览器可录制音频。");
            }
            byte[] pcmBytes = Files.readAllBytes(output);
            if (pcmBytes.length == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频转码失败：输出 PCM 为空，请重新录音。");
            }
            log.info("[SpeechEval] pcmSize={}", pcmBytes.length);
            return pcmBytes;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频转码失败，请重新录音。", ex);
        } finally {
            deleteQuietly(input);
            deleteQuietly(output);
        }
    }

    private String detectSuffix(String originalFilename, String contentType) {
        if (StringUtils.hasText(originalFilename)) {
            String filename = originalFilename.toLowerCase(Locale.ROOT);
            int dot = filename.lastIndexOf('.');
            if (dot >= 0 && dot < filename.length() - 1) return filename.substring(dot);
        }
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (type.contains("webm")) return ".webm";
        if (type.contains("mpeg") || type.contains("mp3")) return ".mp3";
        if (type.contains("wav")) return ".wav";
        if (type.contains("mp4")) return ".mp4";
        if (type.contains("ogg")) return ".ogg";
        return ".audio";
    }

    private void deleteQuietly(Path path) {
        if (path == null) return;
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        return value.length() > 1200 ? value.substring(0, 1200) + "..." : value;
    }
}
