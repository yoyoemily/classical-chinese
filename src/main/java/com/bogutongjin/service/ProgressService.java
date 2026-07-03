package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProgressService {

    private final UserMapper userMapper;
    private final UserWordProgressMapper userWordProgressMapper;
    private final UserArticleProgressMapper userArticleProgressMapper;
    private final UserCheckinMapper userCheckinMapper;
    private final UserAnswerHistoryMapper userAnswerHistoryMapper;

    public Map<String, Object> getProgress(Long userId, String wordBookId) {
        User user = userMapper.selectById(userId);

        // 词进度
        List<UserWordProgress> wordProgresses = userWordProgressMapper.selectList(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId));
        int wordsLearned = wordProgresses.size();
        int wordsMastered = (int) wordProgresses.stream().filter(p -> "done".equals(p.getStage())).count();

        Map<String, Object> wpMap = new LinkedHashMap<>();
        for (UserWordProgress wp : wordProgresses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wordId", wp.getWordId());
            item.put("stage", wp.getStage());
            item.put("nextReviewDate", wp.getNextReviewDate() != null ? wp.getNextReviewDate().toString() : "");
            item.put("correctCount", wp.getCorrectCount());
            item.put("wrongCount", wp.getWrongCount());
            item.put("resetCount", wp.getResetCount());

            // 答题历史
            List<UserAnswerHistory> history = userAnswerHistoryMapper.selectList(
                    new LambdaQueryWrapper<UserAnswerHistory>()
                            .eq(UserAnswerHistory::getUserId, userId)
                            .eq(UserAnswerHistory::getWordId, wp.getWordId())
                            .orderByDesc(UserAnswerHistory::getCreatedAt));
            item.put("history", history.stream().map(h -> {
                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("sentenceId", h.getSentenceId());
                hm.put("selectedOption", h.getSelectedOption());
                hm.put("correct", h.getCorrect() == 1);
                hm.put("timestamp", h.getTimestampMs());
                return hm;
            }).collect(Collectors.toList()));

            wpMap.put(wp.getWordId(), item);
        }

        // 打卡日期
        List<String> checkinDates = userCheckinMapper.selectList(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .orderByAsc(UserCheckin::getCheckinDate))
                .stream().map(c -> c.getCheckinDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .collect(Collectors.toList());

        // 名篇进度
        List<UserArticleProgress> articleProgresses = userArticleProgressMapper.selectList(
                new LambdaQueryWrapper<UserArticleProgress>().eq(UserArticleProgress::getUserId, userId));
        Map<String, Object> apMap = new LinkedHashMap<>();
        for (UserArticleProgress ap : articleProgresses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("articleId", ap.getArticleId());
            item.put("readProgress", ap.getReadProgress());
            item.put("mastery", ap.getMastery());
            item.put("lastReadDate", ap.getLastReadDate() != null ? ap.getLastReadDate().toString() : null);
            apMap.put(ap.getArticleId(), item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wordBookId", wordBookId);
        result.put("wordsLearned", wordsLearned);
        result.put("wordsMastered", wordsMastered);
        result.put("checkinDates", checkinDates);
        result.put("currentStreak", user != null ? user.getCurrentStreak() : 0);
        result.put("longestStreak", user != null ? user.getLongestStreak() : 0);
        result.put("totalXP", user != null ? user.getTotalXp() : 0);
        result.put("wordProgresses", wpMap);
        result.put("articleProgresses", apMap);
        return result;
    }
}
