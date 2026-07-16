package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.common.ResourceNotFoundException;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyService {

    private final WordBookMapper wordBookMapper;
    private final WordBookEntryMapper wordBookEntryMapper;
    private final QuizItemMapper quizItemMapper;
    private final QuizDistractorMapper quizDistractorMapper;
    private final UserWordProgressMapper userWordProgressMapper;
    private final UserAnswerHistoryMapper userAnswerHistoryMapper;
    private final UserCheckinMapper userCheckinMapper;
    private final UserBadgeMapper userBadgeMapper;
    private final BadgeMapper badgeMapper;
    private final UserMapper userMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final StudyMistakeMapper studyMistakeMapper;
    private final StudyMistakeSentenceMapper studyMistakeSentenceMapper;
    private final ArticleKeywordMapper articleKeywordMapper;
    private final ArticleSentenceMapper articleSentenceMapper;
    private final ArticleMapper articleMapper;

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
                .collect(Collectors.toMap(UserWordProgress::getEntryId, p -> p, (a, b) -> a));

        // 获取词书中所有词条
        List<WordBookEntry> allEntries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().eq(WordBookEntry::getWordBookId, wordBookId).orderByAsc(WordBookEntry::getSortOrder));

        LocalDate today = LocalDate.now();
        List<Map<String, Object>> reviewWords = new ArrayList<>();
        List<Map<String, Object>> newWords = new ArrayList<>();

        // 复习词：nextReviewDate <= today 且未完成（按词书 sortOrder 排列，保证顺序稳定）
        for (WordBookEntry e : allEntries) {
            UserWordProgress up = progressMap.get(e.getId());
            if (up == null || "done".equals(up.getStage())) continue;
            if (up.getNextReviewDate() != null && !up.getNextReviewDate().isAfter(today)) {
                Map<String, Object> item = buildTodayEntry(e, up, true, wordBookId);
                if (item != null) reviewWords.add(item);
            }
        }

        // 新学词：没有进度的词
        Set<String> inProgress = progressMap.keySet();
        List<WordBookEntry> newOnes = allEntries.stream()
                .filter(e -> !inProgress.contains(e.getId()))
                .collect(Collectors.toList());

        for (WordBookEntry e : newOnes) {
            Map<String, Object> item = buildTodayEntry(e, null, false, wordBookId);
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

    private Map<String, Object> buildTodayEntry(WordBookEntry entry, UserWordProgress up, boolean isReview, String wordBookId) {
        List<QuizItem> quizItems = quizItemMapper.selectList(
                new LambdaQueryWrapper<QuizItem>().eq(QuizItem::getEntryId, entry.getId()).orderByAsc(QuizItem::getSortOrder));
        if (quizItems.isEmpty()) return null;

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("entryId", entry.getId());
        item.put("character", entry.getCharacter());
        item.put("isReview", isReview);
        if (isReview && up != null) {
            item.put("reviewStage", up.getStage());
        }
        item.put("quizItems", quizItems.stream().map(q -> {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.getId());
            qm.put("definition", q.getDefinition());
            qm.put("difficulty", q.getDifficulty());
            qm.put("targetWord", q.getTargetWord());
            // 干扰项
            List<QuizDistractor> distractors = quizDistractorMapper.selectList(
                    new LambdaQueryWrapper<QuizDistractor>().eq(QuizDistractor::getQuizItemId, q.getId())
                            .orderByAsc(QuizDistractor::getSortOrder));
            qm.put("distractors", distractors.stream().map(QuizDistractor::getText).collect(Collectors.toList()));
            qm.put("kidRef", q.getKidRef());
            // 句子上下文：优先取 quiz_item 自身存储，无则通过 kidRef 从选篇联查
            String sentenceText = q.getSentenceText();
            String sentenceTranslation = q.getSentenceTranslation();
            String sentenceSource = q.getSentenceSource();
            String articleId = "";
            if (q.getKidRef() != null && !q.getKidRef().isEmpty()) {
                if (sentenceText == null || sentenceText.isEmpty()
                    || sentenceTranslation == null || sentenceTranslation.isEmpty()
                    || sentenceSource == null || sentenceSource.isEmpty()) {
                    ArticleKeyword ak = articleKeywordMapper.selectOne(
                            new LambdaQueryWrapper<ArticleKeyword>().eq(ArticleKeyword::getKid, q.getKidRef()));
                    if (ak != null) {
                        ArticleSentence as = articleSentenceMapper.selectById(ak.getArticleSentenceId());
                        if (as != null) {
                            if (sentenceText == null || sentenceText.isEmpty()) sentenceText = as.getText();
                            if (sentenceTranslation == null || sentenceTranslation.isEmpty()) sentenceTranslation = as.getTranslation();
                            if (as.getArticleId() != null) {
                                articleId = as.getArticleId();
                                if (sentenceSource == null || sentenceSource.isEmpty()) {
                                    Article article = articleMapper.selectById(as.getArticleId());
                                    if (article != null) sentenceSource = article.getTitle();
                                }
                            }
                        }
                    }
                } else {
                    // 已有完整句子数据，kidRef 仅用于取 articleId
                    ArticleKeyword ak = articleKeywordMapper.selectOne(
                            new LambdaQueryWrapper<ArticleKeyword>().eq(ArticleKeyword::getKid, q.getKidRef()));
                    if (ak != null) {
                        ArticleSentence as = articleSentenceMapper.selectById(ak.getArticleSentenceId());
                        if (as != null && as.getArticleId() != null) articleId = as.getArticleId();
                    }
                }
            }
            qm.put("sentenceText", sentenceText != null ? sentenceText : "");
            qm.put("sentenceTranslation", sentenceTranslation != null ? sentenceTranslation : "");
            qm.put("sentenceSource", sentenceSource != null ? sentenceSource : "");
            qm.put("articleId", articleId);
            return qm;
        }).collect(Collectors.toList()));
        return item;
    }

    // -------------------- 提交答题 --------------------

    @Transactional
    public Map<String, Object> submitAnswer(Long userId, String wordBookId, String entryId, String quizItemId,
                                             Integer selectedOption, Boolean correct,
                                             String correctAnswerText, String wrongAnswerText) {
        // 记录答题
        UserAnswerHistory history = new UserAnswerHistory();
        history.setUserId(userId);
        history.setWordBookId(wordBookId);
        history.setEntryId(entryId);
        history.setQuizItemId(quizItemId);
        history.setSelectedOption(selectedOption);
        history.setCorrect(correct ? 1 : 0);
        history.setTimestampMs(System.currentTimeMillis());
        userAnswerHistoryMapper.insert(history);

        // 答错记录错题
        if (!correct) {
            recordMistake(userId, wordBookId, entryId, quizItemId, correctAnswerText, wrongAnswerText);
        } else {
            // 答对：增加当前 quiz item 的连续答对次数
            incrementConsecutiveCorrect(userId, entryId, wordBookId, quizItemId);
        }

        // 更新进度
        UserWordProgress progress = userWordProgressMapper.selectOne(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId)
                        .eq(UserWordProgress::getEntryId, entryId));

        if (progress == null) {
            progress = new UserWordProgress();
            progress.setUserId(userId);
            progress.setWordBookId(wordBookId);
            progress.setEntryId(entryId);
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
        // XP 不在答题时发放，改为 completeWord 中逐词发放
        result.put("xpGained", 0);
        return result;
    }

    // -------------------- 完成单个字词（写入 XP） --------------------

    /**
     * 单个字词全部 quiz item 答完后调用（进入字总结页时）。
     * 仅新学词（今天之前无 UserWordProgress 记录）才写入 XP，复习词不给 XP。
     */
    @Transactional
    public Map<String, Object> completeWord(Long userId, String wordBookId, String entryId) {
        // 判断是否为新学词：今天之前是否已有该词的 progress 记录
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        boolean hasExisting = userWordProgressMapper.selectCount(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId)
                        .eq(UserWordProgress::getEntryId, entryId)
                        .lt(UserWordProgress::getCreatedAt, todayStart)) > 0;

        int xpGained = 0;
        if (!hasExisting) {
            // 新学词完成后即时写入 XP
            User user = userMapper.selectById(userId);
            if (user != null) {
                user.setTotalXp(user.getTotalXp() + 10);
                userMapper.updateById(user);
                xpGained = 10;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("xpGained", xpGained);
        return result;
    }

    // -------------------- 完成学习 --------------------

    @Transactional
    public Map<String, Object> completeStudy(Long userId, String wordBookId, Integer correctCount, Integer wrongCount, Integer xpGained) {
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

        // 更新用户（XP 已在 completeWord 中逐词即时写入，此处仅更新连续打卡天数）
        User user = userMapper.selectById(userId);
        if (user != null) {
            user.setCurrentStreak(streak);
            if (streak > user.getLongestStreak()) {
                user.setLongestStreak(streak);
            }
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
        Map<String, Object> newBadge = checkNewBadge(userId, streak);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("newBadge", newBadge);
        result.put("xpGained", xpGained != null ? xpGained : 0);
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

    private Map<String, Object> checkNewBadge(Long userId, int streak) {
        // 获取用户已有勋章
        List<UserBadge> userBadges = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>().eq(UserBadge::getUserId, userId));
        Set<String> earnedIds = userBadges.stream().map(UserBadge::getBadgeId).collect(Collectors.toSet());

        // 获取所有 streak 勋章，按 conditionValue 升序
        List<Badge> allBadges = badgeMapper.selectList(
                new LambdaQueryWrapper<Badge>().eq(Badge::getConditionType, "streak").orderByAsc(Badge::getConditionValue));

        // 找到第一个 streak 达标且未获得的勋章（每天最多跨一个阈值）
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
                return bm;
            }
        }
        return null;
    }

    private int parseStage(String stage) {
        if (stage == null || "done".equals(stage)) return 6;
        try { return Integer.parseInt(stage); } catch (NumberFormatException e) { return 0; }
    }

    // -------------------- 错题本 --------------------

    /** 获取句子文本（通过 quiz_item.kid_ref → article_keyword → article_sentence） */
    private String getSentenceTextByQuizItemId(String quizItemId) {
        QuizItem qi = quizItemMapper.selectById(quizItemId);
        if (qi == null || qi.getKidRef() == null || qi.getKidRef().isEmpty()) return "";
        ArticleKeyword ak = articleKeywordMapper.selectOne(
                new LambdaQueryWrapper<ArticleKeyword>().eq(ArticleKeyword::getKid, qi.getKidRef()));
        if (ak == null) return "";
        ArticleSentence as = articleSentenceMapper.selectById(ak.getArticleSentenceId());
        return as != null ? as.getText() : "";
    }

    /** 答错时记录到错题本 */
    private void recordMistake(Long userId, String wordBookId, String entryId, String quizItemId,
                                String correctAnswerText, String wrongAnswerText) {
        // 查找或创建该字的错题记录（按 user_id + entry_id + word_book_id 唯一）
        StudyMistake mistake = studyMistakeMapper.selectOne(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getEntryId, entryId)
                        .eq(StudyMistake::getWordBookId, wordBookId));

        if (mistake == null) {
            mistake = new StudyMistake();
            mistake.setUserId(userId);
            mistake.setEntryId(entryId);
            mistake.setWordBookId(wordBookId);
            mistake.setTotalErrors(1);
            mistake.setLastMistakeTime(java.time.LocalDateTime.now());
            studyMistakeMapper.insert(mistake);
        } else {
            mistake.setTotalErrors(mistake.getTotalErrors() != null ? mistake.getTotalErrors() + 1 : 1);
            mistake.setLastMistakeTime(java.time.LocalDateTime.now());
            studyMistakeMapper.updateById(mistake);
        }

        // 获取句子文本
        String sentenceText = getSentenceTextByQuizItemId(quizItemId);

        String wrongAnswer = wrongAnswerText != null && !wrongAnswerText.isEmpty()
                ? wrongAnswerText : "不知道";
        String correctAns = correctAnswerText != null && !correctAnswerText.isEmpty()
                ? correctAnswerText : "";

        // 查找已有该题目的记录
        StudyMistakeSentence sentRecord = studyMistakeSentenceMapper.selectOne(
                new LambdaQueryWrapper<StudyMistakeSentence>()
                        .eq(StudyMistakeSentence::getMistakeId, mistake.getId())
                        .eq(StudyMistakeSentence::getQuizItemId, quizItemId));

        if (sentRecord != null) {
            sentRecord.setSentenceText(sentenceText);
            sentRecord.setWrongAnswer(wrongAnswer);
            sentRecord.setCorrectAnswer(correctAns);
            sentRecord.setMistakeCount(sentRecord.getMistakeCount() + 1);
            sentRecord.setConsecutiveCorrect(0);
            studyMistakeSentenceMapper.updateById(sentRecord);
        } else {
            sentRecord = new StudyMistakeSentence();
            sentRecord.setMistakeId(mistake.getId());
            sentRecord.setQuizItemId(quizItemId);
            sentRecord.setSentenceText(sentenceText);
            sentRecord.setWrongAnswer(wrongAnswer);
            sentRecord.setCorrectAnswer(correctAns);
            sentRecord.setMistakeCount(1);
            sentRecord.setConsecutiveCorrect(0);
            studyMistakeSentenceMapper.insert(sentRecord);
        }
    }

    /** 答对时增加该题目的连续答对计数，达到阈值则移出该题目 */
    private void incrementConsecutiveCorrect(Long userId, String entryId, String wordBookId, String quizItemId) {
        StudyMistake mistake = studyMistakeMapper.selectOne(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getEntryId, entryId)
                        .eq(StudyMistake::getWordBookId, wordBookId));
        if (mistake == null) return;

        // 精确查找答对的那个 quiz item
        StudyMistakeSentence sent = studyMistakeSentenceMapper.selectOne(
                new LambdaQueryWrapper<StudyMistakeSentence>()
                        .eq(StudyMistakeSentence::getMistakeId, mistake.getId())
                        .eq(StudyMistakeSentence::getQuizItemId, quizItemId));
        if (sent == null) return;

        sent.setConsecutiveCorrect(sent.getConsecutiveCorrect() + 1);

        if (sent.getConsecutiveCorrect() >= 3) {
            // 该题目达到阈值，移出：外层 total_errors 减去该题目错误次数
            studyMistakeSentenceMapper.deleteById(sent.getId());
            int newTotal = Math.max(0, (mistake.getTotalErrors() != null ? mistake.getTotalErrors() : 0) - sent.getMistakeCount());
            mistake.setTotalErrors(newTotal);
            studyMistakeMapper.updateById(mistake);
            log.info("错题题目已移出: userId={}, entryId={}, quizItemId={}, consecutiveCorrect={}",
                    userId, entryId, quizItemId, sent.getConsecutiveCorrect());
        } else {
            studyMistakeSentenceMapper.updateById(sent);
        }

        // 如果所有题目都被移除了，删除错题主记录
        long remaining = studyMistakeSentenceMapper.selectCount(
                new LambdaQueryWrapper<StudyMistakeSentence>()
                        .eq(StudyMistakeSentence::getMistakeId, mistake.getId()));
        if (remaining == 0) {
            studyMistakeMapper.deleteById(mistake.getId());
            log.info("错题字已全部移出: userId={}, entryId={}", userId, entryId);
        }
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
            WordBookEntry entry = wordBookEntryMapper.selectById(m.getEntryId());

            // 获取该字的所有题目记录
            List<StudyMistakeSentence> sentences = studyMistakeSentenceMapper.selectList(
                    new LambdaQueryWrapper<StudyMistakeSentence>()
                            .eq(StudyMistakeSentence::getMistakeId, m.getId())
                            .orderByDesc(StudyMistakeSentence::getMistakeCount));

            List<Map<String, Object>> sentList = sentences.stream().map(s -> {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("quizItemId", s.getQuizItemId());
                sm.put("sentenceText", s.getSentenceText());
                sm.put("wrongAnswer", s.getWrongAnswer());
                sm.put("correctAnswer", s.getCorrectAnswer());
                sm.put("errorCount", s.getMistakeCount());
                sm.put("consecutiveCorrect", s.getConsecutiveCorrect());
                return sm;
            }).collect(Collectors.toList());

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", m.getEntryId());
            item.put("character", entry != null ? entry.getCharacter() : "");
            item.put("pinyin", entry != null ? entry.getPinyin() : "");
            item.put("totalErrors", m.getTotalErrors() != null ? m.getTotalErrors() : 0);
            item.put("lastErrorTime", m.getLastMistakeTime() != null ? m.getLastMistakeTime().toString().substring(0, 10) : "");
            item.put("sentences", sentList);
            WordBook book = wordBookMapper.selectById(m.getWordBookId());
            item.put("wordBookName", book != null ? book.getName() : "");
            result.add(item);
        }
        return result;
    }

    /** 移除错题 */
    public void removeMistake(Long userId, String wordBookId, String entryId) {
        StudyMistake mistake = studyMistakeMapper.selectOne(
                new LambdaQueryWrapper<StudyMistake>()
                        .eq(StudyMistake::getUserId, userId)
                        .eq(StudyMistake::getEntryId, entryId)
                        .eq(StudyMistake::getWordBookId, wordBookId));
        if (mistake != null) {
            // 先删子表题目记录
            studyMistakeSentenceMapper.delete(
                    new LambdaQueryWrapper<StudyMistakeSentence>()
                            .eq(StudyMistakeSentence::getMistakeId, mistake.getId()));
            // 再删主表记录
            studyMistakeMapper.deleteById(mistake.getId());
        }
    }
}
