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
    private final UserAudioListenLogMapper userAudioListenLogMapper;
    private final ClassicChapterMapper classicChapterMapper;
    private final ClassicParagraphMapper classicParagraphMapper;

    // -------------------- 今日任务 --------------------

    private static final Map<Integer, Integer> EBBINGHAUS_INTERVAL = Map.of(
            0, 0, 1, 1, 2, 2, 3, 4, 4, 7, 5, 15, 6, 30
    );

    /** 生成今日学习任务 */
    public Map<String, Object> getTodayTask(Long userId, String wordBookId, Integer dailyNew, Integer dailyReview) {
        WordBook book = wordBookMapper.selectById(wordBookId);
        if (book == null) throw new ResourceNotFoundException("词书不存在");

        int newLimit = dailyNew != null ? dailyNew : 20;
        int reviewLimit = dailyReview != null ? dailyReview : Integer.MAX_VALUE;

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        // 一次性查询：跨所有词书统计今日新词数（created_at >= 今日 00:00），一次 COUNT 避免 N+1
        long todayNewCount = userWordProgressMapper.selectCount(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .ge(UserWordProgress::getCreatedAt, todayStart));
        boolean dailyNewLimitReached = todayNewCount >= newLimit;

        // 获取用户的词进度（仅当前词书）
        List<UserWordProgress> allProgress = userWordProgressMapper.selectList(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId));

        Map<String, UserWordProgress> progressMap = allProgress.stream()
                .collect(Collectors.toMap(UserWordProgress::getEntryId, p -> p, (a, b) -> a));

        // 获取词书中所有词条
        List<WordBookEntry> allEntries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().eq(WordBookEntry::getWordBookId, wordBookId).orderByAsc(WordBookEntry::getSortOrder));

        // --- 分类：复习词 vs 新学词（先分类，再批量预载） ---
        Set<String> inProgress = progressMap.keySet();
        List<WordBookEntry> reviewEntries = new ArrayList<>();
        List<WordBookEntry> newEntries = new ArrayList<>();

        for (WordBookEntry e : allEntries) {
            UserWordProgress up = progressMap.get(e.getId());
            if (up == null || "done".equals(up.getStage())) {
                if (!inProgress.contains(e.getId())) newEntries.add(e);
                continue;
            }
            if (up.getNextReviewDate() != null && !up.getNextReviewDate().isAfter(today)) {
                reviewEntries.add(e);
            }
        }
        // 截断后再批量预载（只加载实际要返回的 entry）
        if (reviewEntries.size() > reviewLimit) reviewEntries = new ArrayList<>(reviewEntries.subList(0, reviewLimit));
        // 跨词书新词限额：扣除今日已学数量，剩余额度才给本轮
        int remainingNewQuota = (int) Math.max(0, newLimit - todayNewCount);
        if (remainingNewQuota == 0) {
            newEntries.clear();
        } else if (newEntries.size() > remainingNewQuota) {
            newEntries = new ArrayList<>(newEntries.subList(0, remainingNewQuota));
        }

        // --- 批量预载所有子数据（替代 N+1） ---
        Set<String> relevantEntryIds = new LinkedHashSet<>();
        reviewEntries.forEach(e -> relevantEntryIds.add(e.getId()));
        newEntries.forEach(e -> relevantEntryIds.add(e.getId()));

        // 1. 批量 quizItems（按 entryId IN）
        Map<String, List<QuizItem>> qiByEntry = new LinkedHashMap<>();
        if (!relevantEntryIds.isEmpty()) {
            quizItemMapper.selectList(
                    new LambdaQueryWrapper<QuizItem>().in(QuizItem::getEntryId, relevantEntryIds)
                            .orderByAsc(QuizItem::getSortOrder))
                    .forEach(qi -> qiByEntry.computeIfAbsent(qi.getEntryId(), k -> new ArrayList<>()).add(qi));
        }

        // 2. 批量 distractors（按 quizItemId IN）
        Set<String> qiIds = qiByEntry.values().stream().flatMap(List::stream)
                .map(QuizItem::getId).collect(Collectors.toSet());
        Map<String, List<String>> distByQi = new LinkedHashMap<>();
        if (!qiIds.isEmpty()) {
            quizDistractorMapper.selectList(
                    new LambdaQueryWrapper<QuizDistractor>().in(QuizDistractor::getQuizItemId, qiIds)
                            .orderByAsc(QuizDistractor::getSortOrder))
                    .forEach(d -> distByQi.computeIfAbsent(d.getQuizItemId(), k -> new ArrayList<>()).add(d.getText()));
        }

        // 3. 批量 articleKeywords（按 kid IN）
        Set<String> kidRefs = qiByEntry.values().stream().flatMap(List::stream)
                .map(QuizItem::getKidRef).filter(k -> k != null && !k.isEmpty()).collect(Collectors.toSet());
        Map<String, ArticleKeyword> akByKid = new LinkedHashMap<>();
        if (!kidRefs.isEmpty()) {
            articleKeywordMapper.selectList(
                    new LambdaQueryWrapper<ArticleKeyword>().in(ArticleKeyword::getKid, kidRefs))
                    .forEach(ak -> akByKid.put(ak.getKid(), ak));
        }

        // 4. 批量 articleSentences（按 ID IN）
        Set<Long> sentenceIds = akByKid.values().stream()
                .map(ArticleKeyword::getArticleSentenceId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ArticleSentence> sentMap = new LinkedHashMap<>();
        if (!sentenceIds.isEmpty()) {
            articleSentenceMapper.selectBatchIds(sentenceIds).forEach(s -> sentMap.put(s.getId(), s));
        }

        // 5. 批量 articles（按 ID IN）
        Set<String> articleIds = sentMap.values().stream()
                .map(ArticleSentence::getArticleId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, Article> articleMap = new LinkedHashMap<>();
        if (!articleIds.isEmpty()) {
            articleMapper.selectBatchIds(articleIds).forEach(a -> articleMap.put(a.getId(), a));
        }

        // --- 内存组装 ---
        List<Map<String, Object>> reviewWords = new ArrayList<>();
        for (WordBookEntry e : reviewEntries) {
            Map<String, Object> item = assembleTodayEntry(e, progressMap.get(e.getId()), true,
                    qiByEntry, distByQi, akByKid, sentMap, articleMap);
            if (item != null) reviewWords.add(item);
        }

        List<Map<String, Object>> newWords = new ArrayList<>();
        for (WordBookEntry e : newEntries) {
            Map<String, Object> item = assembleTodayEntry(e, null, false,
                    qiByEntry, distByQi, akByKid, sentMap, articleMap);
            if (item != null) newWords.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", today.toString());
        result.put("wordBookId", book.getId());
        result.put("wordBookName", book.getName());
        result.put("reviewWords", reviewWords);
        result.put("newWords", newWords);
        result.put("totalWords", reviewWords.size() + newWords.size());
        result.put("estimatedMinutes", (reviewWords.size() + newWords.size()) * 2);
        result.put("dailyNewLimitReached", dailyNewLimitReached);
        return result;
    }

    /** 从批量预载的 Map 中组装单个 todayEntry（纯内存操作，零 SQL） */
    private Map<String, Object> assembleTodayEntry(WordBookEntry entry, UserWordProgress up, boolean isReview,
            Map<String, List<QuizItem>> qiByEntry, Map<String, List<String>> distByQi,
            Map<String, ArticleKeyword> akByKid, Map<Long, ArticleSentence> sentMap,
            Map<String, Article> articleMap) {
        List<QuizItem> quizItems = qiByEntry.getOrDefault(entry.getId(), List.of());
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
            qm.put("distractors", distByQi.getOrDefault(q.getId(), List.of()));
            qm.put("kidRef", q.getKidRef());

            String sentenceText = q.getSentenceText();
            String sentenceTranslation = q.getSentenceTranslation();
            String sentenceSource = q.getSentenceSource();
            String articleId = "";

            if (q.getKidRef() != null && !q.getKidRef().isEmpty()) {
                ArticleKeyword ak = akByKid.get(q.getKidRef());
                ArticleSentence as = ak != null ? sentMap.get(ak.getArticleSentenceId()) : null;
                boolean needFallback = sentenceText == null || sentenceText.isEmpty()
                        || sentenceTranslation == null || sentenceTranslation.isEmpty()
                        || sentenceSource == null || sentenceSource.isEmpty();
                if (needFallback) {
                    if (as != null) {
                        if (sentenceText == null || sentenceText.isEmpty()) sentenceText = as.getText();
                        if (sentenceTranslation == null || sentenceTranslation.isEmpty()) sentenceTranslation = as.getTranslation();
                        if (as.getArticleId() != null) {
                            articleId = as.getArticleId();
                            if (sentenceSource == null || sentenceSource.isEmpty()) {
                                Article article = articleMap.get(as.getArticleId());
                                if (article != null) sentenceSource = article.getTitle();
                            }
                        }
                    }
                } else {
                    if (as != null && as.getArticleId() != null) articleId = as.getArticleId();
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
     * 硬上限兜底：当日跨词书新词数 >= 50 时拒发 XP，防止绕过前端入口拦截直接调 API。
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
            // 兜底：跨词书新词数硬上限校验（一次 COUNT，正常路径不会触发）
            long todayNewCount = userWordProgressMapper.selectCount(
                    new LambdaQueryWrapper<UserWordProgress>()
                            .eq(UserWordProgress::getUserId, userId)
                            .ge(UserWordProgress::getCreatedAt, todayStart));
            if (todayNewCount >= 50) {
                log.warn("completeWord 硬上限触发: userId={} wordBookId={} entryId={} todayNewCount={}",
                        userId, wordBookId, entryId, todayNewCount);
                Map<String, Object> blocked = new LinkedHashMap<>();
                blocked.put("xpGained", 0);
                return blocked;
            }

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

    // -------------------- 音频听读完成（写入 XP） --------------------

    /** CJK 汉字正则：基本区 + 扩展 A 区，囊括文言文常见繁体/异体 */
    private static final java.util.regex.Pattern CJK_CHAR = java.util.regex.Pattern.compile("[^\\u4e00-\\u9fa5\\u3400-\\u4dbf]");

    /**
     * 音频完整播放完成后调用。去重：同一用户+同一内容只能获取一次 XP。
     * 后端根据 contentId 查出原文，去标点后统计纯汉字字数，10 个汉字 = 1 XP。
     */
    @Transactional
    public Map<String, Object> completeAudioListen(Long userId, String contentType, String contentId) {
        // 查是否已有记录
        boolean exists = userAudioListenLogMapper.exists(
                new LambdaQueryWrapper<UserAudioListenLog>()
                        .eq(UserAudioListenLog::getUserId, userId)
                        .eq(UserAudioListenLog::getContentType, contentType)
                        .eq(UserAudioListenLog::getContentId, contentId));

        int xpGained = 0;
        if (!exists) {
            User user = userMapper.selectById(userId);
            if (user != null) {
                // 根据 contentType 查出原文
                String rawText = fetchContentText(contentType, contentId);
                // 统计纯汉字（去标点、空白、非汉字字符）
                String cleanText = CJK_CHAR.matcher(rawText).replaceAll("");
                int charCount = cleanText.length();
                log.info("[AudioXP] userId={} contentType={} contentId={} rawTextLen={} charCount={} cleanText={}",
                        userId, contentType, contentId, rawText.length(), charCount, cleanText);
                xpGained = charCount / 10;
                if (xpGained > 0) {
                    user.setTotalXp(user.getTotalXp() + xpGained);
                    userMapper.updateById(user);
                }

                // 插入追踪记录（即使 xpGained=0 也记录，防止重复请求）
                UserAudioListenLog log = new UserAudioListenLog();
                log.setUserId(userId);
                log.setContentType(contentType);
                log.setContentId(contentId);
                log.setXpAwarded(xpGained);
                log.setTextLength(charCount);
                userAudioListenLogMapper.insert(log);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("xpGained", xpGained);
        return result;
    }

    /** 根据 contentType + contentId 查出原始文本 */
    private String fetchContentText(String contentType, String contentId) {
        if ("article".equals(contentType)) {
            // article: contentId = articleId，查 article_sentence 拼全文
            List<ArticleSentence> sentences = articleSentenceMapper.selectList(
                    new LambdaQueryWrapper<ArticleSentence>()
                            .eq(ArticleSentence::getArticleId, contentId)
                            .orderByAsc(ArticleSentence::getSortOrder));
            return sentences.stream().map(ArticleSentence::getText).collect(java.util.stream.Collectors.joining());
        } else if ("classic_chapter".equals(contentType)) {
            // classic_chapter: contentId = classicId:nodeId，查 classic_paragraph 拼全文
            try {
                long chapterId = Long.parseLong(contentId.substring(contentId.indexOf(':') + 1));
                List<ClassicParagraph> paragraphs = classicParagraphMapper.selectList(
                        new LambdaQueryWrapper<ClassicParagraph>()
                                .eq(ClassicParagraph::getChapterId, chapterId)
                                .orderByAsc(ClassicParagraph::getSortOrder));
                return paragraphs.stream().map(ClassicParagraph::getText).collect(java.util.stream.Collectors.joining());
            } catch (Exception e) {
                log.warn("Failed to parse classic chapter contentId: {}", contentId, e);
                return "";
            }
        }
        return "";
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

    /** 获取句子文本（直接从 quiz_item.sentence_text 取） */
    private String getSentenceTextByQuizItemId(String quizItemId) {
        QuizItem qi = quizItemMapper.selectById(quizItemId);
        if (qi == null) return "";
        return qi.getSentenceText() != null ? qi.getSentenceText() : "";
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
            sentRecord.setUserId(userId);
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

    /** 获取错题数量（仅 COUNT，供首页等只需要数量的场景） */
    public Map<String, Object> getMistakeCount(Long userId, String wordBookId) {
        LambdaQueryWrapper<StudyMistake> wrapper = new LambdaQueryWrapper<StudyMistake>()
                .eq(StudyMistake::getUserId, userId);
        if (wordBookId != null && !wordBookId.isEmpty()) {
            wrapper.eq(StudyMistake::getWordBookId, wordBookId);
        }
        long count = studyMistakeMapper.selectCount(wrapper);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("count", count);
        return result;
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
        if (mistakes.isEmpty()) return List.of();

        // --- 批量预载，替代循环内 N+1 ---
        // entryIds + bookIds
        Set<String> entryIds = new LinkedHashSet<>();
        Set<String> bookIds = new LinkedHashSet<>();
        for (StudyMistake m : mistakes) {
            entryIds.add(m.getEntryId());
            bookIds.add(m.getWordBookId());
        }

        Map<String, WordBookEntry> entryMap = new LinkedHashMap<>();
        if (!entryIds.isEmpty()) {
            wordBookEntryMapper.selectBatchIds(entryIds)
                    .forEach(e -> entryMap.put(e.getId(), e));
        }

        Map<String, WordBook> bookMap = new LinkedHashMap<>();
        if (!bookIds.isEmpty()) {
            wordBookMapper.selectBatchIds(bookIds)
                    .forEach(b -> bookMap.put(b.getId(), b));
        }

        // 批量 sentences（按 mistakeId IN）
        Set<Long> mistakeIds = mistakes.stream().map(StudyMistake::getId).collect(Collectors.toSet());
        Map<Long, List<StudyMistakeSentence>> sentByMistake = new LinkedHashMap<>();
        if (!mistakeIds.isEmpty()) {
            studyMistakeSentenceMapper.selectList(
                    new LambdaQueryWrapper<StudyMistakeSentence>()
                            .in(StudyMistakeSentence::getMistakeId, mistakeIds)
                            .orderByDesc(StudyMistakeSentence::getMistakeCount))
                    .forEach(s -> sentByMistake.computeIfAbsent(s.getMistakeId(), k -> new ArrayList<>()).add(s));
        }

        // --- 内存组装 ---
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudyMistake m : mistakes) {
            WordBookEntry entry = entryMap.get(m.getEntryId());
            WordBook book = bookMap.get(m.getWordBookId());
            List<StudyMistakeSentence> sentences = sentByMistake.getOrDefault(m.getId(), List.of());

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
