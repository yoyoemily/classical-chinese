package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.common.BusinessException;
import com.bogutongjin.common.ResourceNotFoundException;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyService {

    private final WordBookMapper wordBookMapper;
    private final WordMapper wordMapper;
    private final SentenceMapper sentenceMapper;
    private final SentenceDistractorMapper sentenceDistractorMapper;
    private final UserWordProgressMapper userWordProgressMapper;
    private final UserAnswerHistoryMapper userAnswerHistoryMapper;
    private final UserCheckinMapper userCheckinMapper;
    private final UserBadgeMapper userBadgeMapper;
    private final BadgeMapper badgeMapper;
    private final UserMapper userMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final StudyMistakeMapper studyMistakeMapper;

    // -------------------- 今日任务 --------------------

    private static final Map<Integer, Integer> EBBINGHAUS_INTERVAL = Map.of(
            0, 0, 1, 1, 2, 2, 3, 4, 4, 7, 5, 15, 6, 30
    );

    /** 生成今日学习任务 */
    public Map<String, Object> getTodayTask(Long userId, String wordBookId, Integer dailyNew, Integer dailyReview) {
        WordBook book = wordBookMapper.selectById(wordBookId);
        if (book == null) throw new ResourceNotFoundException("词书不存在");

        // 未传则使用默认值
        int newLimit = dailyNew != null ? dailyNew : 20;
        int reviewLimit = dailyReview != null ? dailyReview : Integer.MAX_VALUE;

        // 获取用户的词进度
        List<UserWordProgress> allProgress = userWordProgressMapper.selectList(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId));

        Map<String, UserWordProgress> progressMap = allProgress.stream()
                .collect(Collectors.toMap(UserWordProgress::getWordId, p -> p, (a, b) -> a));

        // 获取词书中所有词
        List<Word> allWords = wordMapper.selectList(
                new LambdaQueryWrapper<Word>().eq(Word::getWordBookId, wordBookId).orderByAsc(Word::getSortOrder));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> reviewWords = new ArrayList<>();
        List<Map<String, Object>> newWords = new ArrayList<>();

        // 复习词：nextReviewDate <= today 且未完成
        for (UserWordProgress up : allProgress) {
            if ("done".equals(up.getStage())) continue;
            if (up.getNextReviewDate() != null && !up.getNextReviewDate().isAfter(today)) {
                Word w = findWord(allWords, up.getWordId());
                if (w != null) {
                    Map<String, Object> item = buildTodayWord(w, up, true, wordBookId);
                    if (item != null) reviewWords.add(item);
                }
            }
        }

        // 新学词：没有进度的词
        Set<String> inProgress = progressMap.keySet();
        List<Word> newOnes = allWords.stream()
                .filter(w -> !inProgress.contains(w.getId()))
                .collect(Collectors.toList());

        for (Word w : newOnes) {
            Map<String, Object> item = buildTodayWord(w, null, false, wordBookId);
            if (item != null) newWords.add(item);
        }

        // 按参数截断
        if (reviewWords.size() > reviewLimit) reviewWords = reviewWords.subList(0, reviewLimit);
        if (newWords.size() > newLimit) newWords = newWords.subList(0, newLimit);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.toString());
        result.put("wordBookId", book.getId());
        result.put("wordBookName", book.getName());
        result.put("reviewWords", reviewWords);
        result.put("newWords", newWords);
        result.put("totalWords", reviewWords.size() + newWords.size());
        result.put("estimatedMinutes", (reviewWords.size() + newWords.size()) * 2);
        return result;
    }

    private Word findWord(List<Word> words, String wordId) {
        return words.stream().filter(w -> w.getId().equals(wordId)).findFirst().orElse(null);
    }

    private Map<String, Object> buildTodayWord(Word w, UserWordProgress up, boolean isReview, String wordBookId) {
        List<Sentence> sentences = sentenceMapper.selectList(
                new LambdaQueryWrapper<Sentence>().eq(Sentence::getWordId, w.getId()).orderByAsc(Sentence::getSortOrder));
        if (sentences.isEmpty()) return null;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("wordId", w.getId());
        item.put("character", w.getCharacter());
        item.put("isReview", isReview);
        if (isReview && up != null) {
            item.put("reviewStage", up.getStage());
        }
        item.put("sentences", sentences.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("text", s.getText());
            sm.put("source", s.getSource());
            sm.put("translation", s.getTranslation());
            sm.put("targetWord", s.getTargetWord());
            sm.put("correctMeaningIndex", s.getCorrectMeaningIndex());
            sm.put("difficulty", s.getDifficulty());
            // 干扰项
            List<SentenceDistractor> distractors = sentenceDistractorMapper.selectList(
                    new LambdaQueryWrapper<SentenceDistractor>().eq(SentenceDistractor::getSentenceId, s.getId())
                            .orderByAsc(SentenceDistractor::getSortOrder));
            sm.put("distractors", distractors.stream().map(SentenceDistractor::getText).collect(Collectors.toList()));
            return sm;
        }).collect(Collectors.toList()));
        return item;
    }

    // -------------------- 提交答题 --------------------

    @Transactional
    public Map<String, Object> submitAnswer(Long userId, String wordBookId, String wordId, String sentenceId,
                                             Integer selectedOption, Boolean correct) {
        // 记录答题
        UserAnswerHistory history = new UserAnswerHistory();
        history.setUserId(userId);
        history.setWordBookId(wordBookId);
        history.setWordId(wordId);
        history.setSentenceId(sentenceId);
        history.setSelectedOption(selectedOption);
        history.setCorrect(correct ? 1 : 0);
        history.setTimestampMs(System.currentTimeMillis());
        userAnswerHistoryMapper.insert(history);

        // 答错：记录/更新错题本
        if (!correct) {
            recordMistake(userId, wordBookId, wordId, sentenceId, selectedOption);
        } else {
            // 答对：增加连续答对次数
            incrementConsecutiveCorrect(userId, wordId);
        }

        // 更新进度
        UserWordProgress progress = userWordProgressMapper.selectOne(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId)
                        .eq(UserWordProgress::getWordId, wordId));

        if (progress == null) {
            progress = new UserWordProgress();
            progress.setUserId(userId);
            progress.setWordBookId(wordBookId);
            progress.setWordId(wordId);
            progress.setStage("0");
            progress.setCorrectCount(0);
            progress.setWrongCount(0);
            progress.setResetCount(0);
        }

        // 答对：推进阶段
        if (correct) {
            progress.setCorrectCount(progress.getCorrectCount() + 1);
            int currentStage = parseStage(progress.getStage());
            int nextStage = Math.min(currentStage + 1, 6);
            progress.setStage(String.valueOf(nextStage));
            Integer interval = EBBINGHAUS_INTERVAL.get(nextStage);
            progress.setNextReviewDate(LocalDate.now().plusDays(interval != null ? interval : 0));
            if (nextStage >= 6) {
                progress.setStage("done");
            }
        } else {
            // 答错：回退
            progress.setWrongCount(progress.getWrongCount() + 1);
            int currentStage = parseStage(progress.getStage());
            int prevStage = Math.max(currentStage - 1, 0);
            if (prevStage == 0 && currentStage == 0) {
                progress.setResetCount(progress.getResetCount() + 1);
            }
            progress.setStage(String.valueOf(prevStage));
            Integer interval = EBBINGHAUS_INTERVAL.get(prevStage);
            progress.setNextReviewDate(LocalDate.now().plusDays(interval != null ? interval : 0));
        }

        if (progress.getId() == null) {
            userWordProgressMapper.insert(progress);
        } else {
            userWordProgressMapper.updateById(progress);
        }

        Map<String, Object> updated = new LinkedHashMap<>();
        updated.put("stage", progress.getStage());
        updated.put("nextReviewDate", progress.getNextReviewDate() != null ? progress.getNextReviewDate().toString() : "");
        updated.put("correctCount", progress.getCorrectCount());
        updated.put("wrongCount", progress.getWrongCount());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("updatedProgress", updated);
        return result;
    }

    // -------------------- 完成学习 --------------------

    @Transactional
    public Map<String, Object> completeStudy(Long userId, String wordBookId, Integer correctCount, Integer wrongCount) {
        LocalDate today = LocalDate.now();

        // 打卡
        boolean exists = userCheckinMapper.exists(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .eq(UserCheckin::getCheckinDate, today));
        if (!exists) {
            UserCheckin checkin = new UserCheckin();
            checkin.setUserId(userId);
            checkin.setCheckinDate(today);
            userCheckinMapper.insert(checkin);
        }

        // 计算连续天数
        int streak = calcStreak(userId, today);

        // 更新用户
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setCurrentStreak(streak);
            if (streak > user.getLongestStreak()) {
                user.setLongestStreak(streak);
            }
            int xp = correctCount * 10;
            user.setTotalXp(user.getTotalXp() + xp);
            userMapper.updateById(user);
        }

        // 更新每日任务
        DailyTask task = dailyTaskMapper.selectOne(
                new LambdaQueryWrapper<DailyTask>()
                        .eq(DailyTask::getUserId, userId)
                        .eq(DailyTask::getDate, today)
                        .eq(DailyTask::getWordBookId, wordBookId));
        if (task != null) {
            task.setCompletedCount(task.getCompletedCount() + correctCount + wrongCount);
            task.setCorrectCount(task.getCorrectCount() + correctCount);
            task.setWrongCount(task.getWrongCount() + wrongCount);
            if (task.getCompletedCount() >= task.getTotalWords()) {
                task.setStatus(1);
            }
            dailyTaskMapper.updateById(task);
        }

        // 检查新勋章
        List<Map<String, Object>> newBadges = checkNewBadges(userId, streak);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newBadges", newBadges);
        result.put("xpGained", correctCount * 10);
        return result;
    }

    private int calcStreak(Long userId, LocalDate today) {
        List<UserCheckin> records = userCheckinMapper.selectList(
                new LambdaQueryWrapper<UserCheckin>()
                        .eq(UserCheckin::getUserId, userId)
                        .orderByDesc(UserCheckin::getCheckinDate));
        int streak = 0;
        LocalDate expected = today;
        for (UserCheckin r : records) {
            if (r.getCheckinDate().equals(expected)) {
                streak++;
                expected = expected.minusDays(1);
            } else if (r.getCheckinDate().isBefore(expected)) {
                break;
            }
        }
        return streak;
    }

    private List<Map<String, Object>> checkNewBadges(Long userId, int streak) {
        // 获取用户已有勋章
        List<UserBadge> userBadges = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>().eq(UserBadge::getUserId, userId));
        Set<String> earnedIds = userBadges.stream().map(UserBadge::getBadgeId).collect(Collectors.toSet());

        // 获取所有 streak 勋章
        List<Badge> allBadges = badgeMapper.selectList(
                new LambdaQueryWrapper<Badge>().eq(Badge::getConditionType, "streak").orderByAsc(Badge::getConditionValue));

        List<Map<String, Object>> newBadges = new ArrayList<>();
        for (Badge badge : allBadges) {
            if (!earnedIds.contains(badge.getId()) && streak >= badge.getConditionValue()) {
                UserBadge ub = new UserBadge();
                ub.setUserId(userId);
                ub.setBadgeId(badge.getId());
                ub.setEarnedDate(LocalDate.now());
                ub.setNotified(0);
                userBadgeMapper.insert(ub);

                Map<String, Object> bm = new LinkedHashMap<>();
                bm.put("id", badge.getId());
                bm.put("name", badge.getName());
                bm.put("description", badge.getDescription());
                bm.put("icon", badge.getIcon());
                bm.put("category", badge.getCategory());
                Map<String, Object> cond = new LinkedHashMap<>();
                cond.put("type", badge.getConditionType());
                cond.put("value", badge.getConditionValue());
                bm.put("condition", cond);
                newBadges.add(bm);
            }
        }
        return newBadges;
    }

    private int parseStage(String stage) {
        if (stage == null || "done".equals(stage)) return 6;
        try { return Integer.parseInt(stage); } catch (NumberFormatException e) { return 0; }
    }

    // -------------------- 错题本 --------------------

    /** 记录错题：答错时调用 */
    private void recordMistake(Long userId, String wordBookId, String wordId, String sentenceId, Integer selectedOption) {
        // 查询已有记录
        StudyMistake existing = studyMistakeMapper.selectOne(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getWordId, wordId));

        // 获取句子信息和干扰项
        com.bogutongjin.entity.Sentence sentence = sentenceMapper.selectById(sentenceId);
        String sentenceText = sentence != null ? sentence.getText() : "";
        String correctAnswer = "选项" + (getCorrectMeaningIndex(sentenceId) + 1);
        if (sentence != null) {
            List<SentenceDistractor> distractors = sentenceDistractorMapper.selectList(
                    new LambdaQueryWrapper<SentenceDistractor>()
                            .eq(SentenceDistractor::getSentenceId, sentenceId)
                            .orderByAsc(SentenceDistractor::getSortOrder));
            int ci = sentence.getCorrectMeaningIndex();
            if (ci >= 0 && ci < distractors.size()) {
                correctAnswer = distractors.get(ci).getText();
            }
        }

        String wrongAnswer = selectedOption != null && selectedOption >= 0
                ? getDistractorText(sentenceId, selectedOption)
                : "不知道";

        if (existing != null) {
            existing.setSentenceText(sentenceText);
            existing.setWrongAnswer(wrongAnswer);
            existing.setCorrectAnswer(correctAnswer);
            existing.setMistakeCount(existing.getMistakeCount() + 1);
            existing.setLastMistakeTime(java.time.LocalDateTime.now());
            existing.setConsecutiveCorrect(0); // 再次答错，重置连续答对计数
            studyMistakeMapper.updateById(existing);
        } else {
            StudyMistake mistake = new StudyMistake();
            mistake.setUserId(userId);
            mistake.setWordBookId(wordBookId);
            mistake.setWordId(wordId);
            mistake.setSentenceText(sentenceText);
            mistake.setWrongAnswer(wrongAnswer);
            mistake.setCorrectAnswer(correctAnswer);
            mistake.setMistakeCount(1);
            mistake.setLastMistakeTime(java.time.LocalDateTime.now());
            mistake.setConsecutiveCorrect(0);
            studyMistakeMapper.insert(mistake);
        }
    }

    /** 答对时增加连续答对计数 */
    private void incrementConsecutiveCorrect(Long userId, String wordId) {
        StudyMistake existing = studyMistakeMapper.selectOne(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getWordId, wordId));
        if (existing != null) {
            existing.setConsecutiveCorrect(existing.getConsecutiveCorrect() + 1);
            studyMistakeMapper.updateById(existing);
        }
    }

    private int getCorrectMeaningIndex(String sentenceId) {
        com.bogutongjin.entity.Sentence s = sentenceMapper.selectById(sentenceId);
        return s != null ? s.getCorrectMeaningIndex() : 0;
    }

    private String getDistractorText(String sentenceId, int index) {
        List<SentenceDistractor> distractors = sentenceDistractorMapper.selectList(
                new LambdaQueryWrapper<SentenceDistractor>()
                        .eq(SentenceDistractor::getSentenceId, sentenceId)
                        .orderByAsc(SentenceDistractor::getSortOrder));
        if (index >= 0 && index < distractors.size()) {
            return distractors.get(index).getText();
        }
        return "选项" + (index + 1);
    }

    /** 获取错题列表 */
    public List<Map<String, Object>> getMistakes(Long userId, String wordBookId) {
        LambdaQueryWrapper<StudyMistake> wrapper = new LambdaQueryWrapper<StudyMistake>()
                .eq(StudyMistake::getUserId, userId)
                .orderByDesc(StudyMistake::getLastMistakeTime);
        if (wordBookId != null && !wordBookId.isEmpty()) {
            wrapper.eq(StudyMistake::getWordBookId, wordBookId);
        }

        List<StudyMistake> mistakes = studyMistakeMapper.selectList(wrapper);
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudyMistake m : mistakes) {
            Word word = wordMapper.selectById(m.getWordId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wordId", m.getWordId());
            item.put("character", word != null ? word.getCharacter() : "");
            item.put("pinyin", word != null ? word.getPinyin() : "");
            item.put("sentenceText", m.getSentenceText());
            item.put("sentenceId", "");
            item.put("wrongAnswer", m.getWrongAnswer());
            item.put("correctAnswer", m.getCorrectAnswer());
            item.put("errorCount", m.getMistakeCount());
            item.put("lastErrorTime", m.getLastMistakeTime() != null ? m.getLastMistakeTime().toString().substring(0, 10) : "");
            item.put("consecutiveCorrect", m.getConsecutiveCorrect());
            result.add(item);
        }
        return result;
    }

    /** 移除错题 */
    public void removeMistake(Long userId, String wordId) {
        studyMistakeMapper.delete(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getWordId, wordId));
    }
}
