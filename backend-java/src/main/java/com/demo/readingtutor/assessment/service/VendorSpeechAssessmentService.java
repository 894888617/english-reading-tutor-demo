package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.ai.providers.SpeechEvalProvider;
import com.demo.readingtutor.assessment.audio.AudioTranscodeService;
import com.demo.readingtutor.ai.types.AiTypes.SpeechEvalResult;
import com.demo.readingtutor.assessment.dto.PronunciationIssue;
import com.demo.readingtutor.assessment.dto.ReadingAssessmentResult;
import com.demo.readingtutor.assessment.dto.ReadingScore;
import com.demo.readingtutor.assessment.dto.WordToken;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
@ConditionalOnExpression("!${ai.mock-enabled:false}")
@Primary
public class VendorSpeechAssessmentService implements SpeechAssessmentService {
    private final SpeechEvalProvider provider;
    private final EvaluationRecordService recordService;
    private final AudioTranscodeService audioTranscodeService;

    public VendorSpeechAssessmentService(SpeechEvalProvider provider, EvaluationRecordService recordService, AudioTranscodeService audioTranscodeService) {
        this.provider = provider; this.recordService = recordService; this.audioTranscodeService = audioTranscodeService;
    }

    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText) {
        return assess(audio, targetText, null);
    }

    @Override
    public ReadingAssessmentResult assess(MultipartFile audio, String targetText, String recognizedText) {
        try {
            byte[] pcmBytes = audioTranscodeService.toPcm16kMono(audio);
            SpeechEvalResult eval = provider.evaluate(pcmBytes, targetText, "en", null, null, "audio/L16;rate=16000", audio.getOriginalFilename());
            ReadingScore score = new ReadingScore(eval.totalScore(), eval.accuracyScore(), eval.fluencyScore(), eval.completenessScore(), eval.clarityScore());
            List<WordToken> tokens = new ArrayList<>();
            List<PronunciationIssue> issues = new ArrayList<>();
            for (int i = 0; i < eval.words().size(); i++) {
                var word = eval.words().get(i);
                String status = word.correct() ? "correct" : "wrong";
                tokens.add(new WordToken(i, word.word(), normalize(word.word()), null, status));
                if (!word.correct()) {
                    issues.add(new PronunciationIssue("wrong", word.word(), word.actualWord(), i,
                            "单词 “" + word.word() + "” 需要再练习。", "请点击错词听标准发音，然后慢速跟读。"));
                }
            }
            String feedback = feedback(score, issues);
            return recordService.save(new ReadingAssessmentResult(null, targetText, recognizedText == null ? "" : recognizedText, score, tokens, issues, feedback, null, null, true));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "评分失败，请重新录音", ex);
        }
    }

    private String normalize(String word) { return word == null ? "" : word.toLowerCase().replaceAll("[^a-z0-9']", ""); }
    private String feedback(ReadingScore score, List<PronunciationIssue> issues) {
        if (score.totalScore() >= 90 && issues.isEmpty()) return "Excellent pronunciation! Great job!";
        if (issues.isEmpty()) return "Good job! Try to read a little more smoothly.";
        return "Good try. Please read “" + issues.get(0).targetWord() + "” again.";
    }
}
