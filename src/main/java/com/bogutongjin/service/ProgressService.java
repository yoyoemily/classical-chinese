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
    private final UserCheckinMapper userCheckinMapper;
    private final UserAnswerHistoryMapper userAnswerHistoryMapper;
    private final BadgeMapper badgeMapper;
    private final UserBadgeMapper userBadgeMapper;

    public Map<String, Object> getProgress(Long userId, String wordBookId) {
        User user = userMapper.selectById(userId);

        // 词进度
        List<UserWordProgress> wordProgresses = userWordProgressMapper.selectList(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId));
        int wordsLearned = wordProgresses.size();
        int wordsMastered = (int) wordProgresses.stream().filter(p -> "done".equals(p.getStage())).count();

        // 批量预载答题历史（替代循环内 N+1）
        Set<String> entryIds = wordProgresses.stream().map(UserWordProgress::getEntryId).collect(Collectors.toSet());
        Map<String, List<Map<String, Object>>> historyByEntry = new LinkedHashMap<>();
        if (!entryIds.isEmpty()) {
            List<UserAnswerHistory> allHistory = userAnswerHistoryMapper.selectList(
                    new LambdaQueryWrapper<UserAnswerHistory>()
                            .eq(UserAnswerHistory::getUserId, userId)
                            .in(UserAnswerHistory::getEntryId, entryIds)
                            .orderByDesc(UserAnswerHistory::getCreatedAt));
            for (UserAnswerHistory h : allHistory) {
                Map<String, Object> hm = new LinkedHashMap<>();
                hm.put("quizItemId", h.getQuizItemId());
                hm.put("selectedOption", h.getSelectedOption());
                hm.put("correct", h.getCorrect() == 1);
                hm.put("timestamp", h.getTimestampMs());
                historyByEntry.computeIfAbsent(h.getEntryId(), k -> new ArrayList<>()).add(hm);
            }
        }

        Map<String, Object> wpMap = new LinkedHashMap<>();
        for (UserWordProgress wp : wordProgresses) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", wp.getEntryId());
            item.put("stage", wp.getStage());
            item.put("nextReviewDate", wp.getNextReviewDate() != null ? wp.getNextReviewDate().toString() : "");
            item.put("correctCount", wp.getCorrectCount());
            item.put("wrongCount", wp.getWrongCount());
            item.put("resetCount", wp.getResetCount());
            item.put("history", historyByEntry.getOrDefault(wp.getEntryId(), List.of()));
            wpMap.put(wp.getEntryId(), item);
        }

        // 打卡日期
        List<String> checkinDates = userCheckinMapper.selectList(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .orderByAsc(UserCheckin::getCheckinDate))
                .stream().map(c -> c.getCheckinDate().format(DateTimeFormatter.ISO_LOCAL_DATE))
                .collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wordBookId", wordBookId);
        result.put("wordsLearned", wordsLearned);
        result.put("wordsMastered", wordsMastered);
        result.put("checkinDates", checkinDates);
        int streak = user != null ? user.getCurrentStreak() : 0;
        result.put("currentStreak", streak);
        result.put("longestStreak", user != null ? user.getLongestStreak() : 0);
        result.put("totalXP", user != null ? user.getTotalXp() : 0);
        result.put("wordProgresses", wpMap);

        // 下一个可获勋章：全部 streak 勋章中，取 gap 最小的未获得勋章
        Set<String> earnedIds = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>().eq(UserBadge::getUserId, userId))
                .stream().map(UserBadge::getBadgeId).collect(Collectors.toSet());
        List<Badge> allBadges = badgeMapper.selectList(
                new LambdaQueryWrapper<Badge>().eq(Badge::getConditionType, "streak").orderByAsc(Badge::getConditionValue));
        Map<String, Object> nextBadge = null;
        int bestGap = Integer.MAX_VALUE;
        for (Badge badge : allBadges) {
            if (!earnedIds.contains(badge.getId())) {
                int gap = Math.max(0, badge.getConditionValue() - streak);
                if (gap < bestGap) {
                    bestGap = gap;
                    Map<String, Object> nb = new LinkedHashMap<>();
                    nb.put("id", badge.getId());
                    nb.put("name", badge.getName());
                    nb.put("icon", badge.getIcon());
                    nb.put("description", badge.getDescription());
                    nb.put("gap", gap);
                    nb.put("gapUnit", "天");
                    nextBadge = nb;
                }
            }
        }
        result.put("nextBadge", nextBadge);

        return result;
    }
}
