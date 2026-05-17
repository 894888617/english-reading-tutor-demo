package com.demo.readingtutor.assessment.service;

import com.demo.readingtutor.assessment.dto.PronunciationIssue;
import com.demo.readingtutor.assessment.dto.WordToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PronunciationDiffService {
    private static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?|\\d+");

    public DiffResult diff(String targetText, String recognizedText) {
        List<String> target = words(targetText);
        List<String> actual = words(recognizedText);
        int[][] dp = new int[target.size() + 1][actual.size() + 1];
        for (int i = target.size() - 1; i >= 0; i--) {
            for (int j = actual.size() - 1; j >= 0; j--) {
                dp[i][j] = target.get(i).equals(actual.get(j)) ? dp[i + 1][j + 1] + 1 : Math.max(dp[i + 1][j], dp[i][j + 1]);
            }
        }

        List<WordToken> tokens = new ArrayList<>();
        List<PronunciationIssue> issues = new ArrayList<>();
        int i = 0, j = 0;
        while (i < target.size() && j < actual.size()) {
            String targetWord = target.get(i);
            String actualWord = actual.get(j);
            if (targetWord.equals(actualWord)) {
                tokens.add(new WordToken(tokens.size(), displayWord(targetText, i, targetWord), targetWord, null, "correct"));
                i++; j++;
            } else if (i + 1 < target.size() && target.get(i + 1).equals(actualWord)) {
                addMissed(tokens, issues, targetText, i, targetWord);
                i++;
            } else if (j + 1 < actual.size() && targetWord.equals(actual.get(j + 1))) {
                addExtra(tokens, issues, actualWord);
                j++;
            } else if (dp[i + 1][j] > dp[i][j + 1]) {
                addMissed(tokens, issues, targetText, i, targetWord);
                i++;
            } else if (dp[i + 1][j] < dp[i][j + 1]) {
                addExtra(tokens, issues, actualWord);
                j++;
            } else {
                tokens.add(new WordToken(tokens.size(), displayWord(targetText, i, targetWord), targetWord, null, "wrong"));
                issues.add(new PronunciationIssue("wrong", targetWord, actualWord, tokens.size() - 1,
                        targetWord + " 读成了 " + actualWord,
                        "请听标准发音后再跟读这个词：" + targetWord + "。"));
                i++; j++;
            }
        }
        while (i < target.size()) {
            addMissed(tokens, issues, targetText, i, target.get(i));
            i++;
        }
        while (j < actual.size()) {
            addExtra(tokens, issues, actual.get(j));
            j++;
        }
        return new DiffResult(tokens, issues, target.size(), actual.size());
    }

    private void addMissed(List<WordToken> tokens, List<PronunciationIssue> issues, String targetText, int targetIndex, String targetWord) {
        tokens.add(new WordToken(tokens.size(), displayWord(targetText, targetIndex, targetWord), targetWord, null, "missed"));
        issues.add(new PronunciationIssue("missed", targetWord, null, tokens.size() - 1,
                targetWord + " 漏读了", "这个词虽然短，也要在句子里清楚读出来。"));
    }

    private void addExtra(List<WordToken> tokens, List<PronunciationIssue> issues, String actualWord) {
        tokens.add(new WordToken(tokens.size(), actualWord, actualWord, null, "extra"));
        issues.add(new PronunciationIssue("extra", null, actualWord, tokens.size() - 1,
                "多读了 " + actualWord, "先看准目标句，再自然地读完整句。"));
    }

    private List<String> words(String text) {
        List<String> result = new ArrayList<>();
        Matcher matcher = WORD_PATTERN.matcher(StringUtils.hasText(text) ? text : "");
        while (matcher.find()) {
            result.add(normalize(matcher.group()));
        }
        return result;
    }

    private String normalize(String word) {
        return word.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']", "");
    }

    private String displayWord(String originalText, int wordIndex, String fallback) {
        Matcher matcher = WORD_PATTERN.matcher(StringUtils.hasText(originalText) ? originalText : "");
        int index = 0;
        while (matcher.find()) {
            if (index == wordIndex) {
                return matcher.group();
            }
            index++;
        }
        return fallback;
    }

    public record DiffResult(List<WordToken> wordResults, List<PronunciationIssue> issues, int targetWordCount, int actualWordCount) {}
}
