package com.bogutongjin.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.bogutongjin.dto.GlossaryImportRequest;
import com.bogutongjin.dto.GlossaryImportRequest.*;
import com.bogutongjin.dto.SourceData;
import com.bogutongjin.dto.SourceData.*;
import com.bogutongjin.entity.Classic;
import com.bogutongjin.mapper.ClassicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 冷启动数据导入服务
 * 读取 source.json，解析并批量导入 MySQL 所有业务表
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataImportService {

    private final JdbcTemplate jdbc;
    private final ClassicMapper classicMapper;

    @Value("${app.source-data-path:classpath:source.json}")
    private String sourceDataPath;

    /** 知识库选篇正文 JSON 文件路径 */
    @Value("${app.articles-data-path:/Users/zhutx/Documents/knowledge_library/文言文/选篇/正文/articles.json}")
    private String articlesDataPath;

    @Transactional(rollbackFor = Exception.class)
    public void importFromJson() {
        String json;
        String path = sourceDataPath;
        try {
            if (path.startsWith("classpath:")) {
                ClassPathResource resource = new ClassPathResource(path.substring("classpath:".length()));
                try (InputStream in = resource.getInputStream()) {
                    json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            throw new RuntimeException("读取数据源文件失败: " + sourceDataPath, e);
        }
        SourceData source = JSONUtil.toBean(json, SourceData.class);
        log.info("数据源解析完成: {} 本词书, {} 枚勋章, {} 部经典",
                source.getWordBooks().size(),
                source.getBadges() != null ? source.getBadges().size() : 0,
                source.getClassics() != null ? source.getClassics().size() : 0);

        truncateAll();
        importBadges(source.getBadges());
        importWordBooks(source.getWordBooks());
        importClassics(source.getClassics());

        log.info("数据源导入完成");
    }

    /**
     * 选篇正文全量导入（幂等：先清空后插入）
     * 从知识库 articles.json 读取 55 篇选篇正文
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importArticlesFromJson() {
        String json;
        String path = articlesDataPath;
        try {
            json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Path.of(path)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("读取选篇正文文件失败: " + path, e);
        }
        List<SourceArticle> articles = JSONUtil.toList(json, SourceArticle.class);

        // 清空文章相关表（article_glossary 由 importGlossaryForArticle 独立管理，不 TRUNCATE）
        String[] tables = {
                "article_keyword", "article_char_annotation",
                "article_sentence", "article"
        };
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : tables) {
            jdbc.execute("TRUNCATE TABLE " + t);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("已清空 {} 张文章相关表", tables.length);

        // 导入
        importArticles(articles);

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("success", true);
        result.put("count", articles.size());
        result.put("message", "选篇正文导入完成");
        return result;
    }

    /**
     * 单篇典故注释导入
     * 先删除该篇所有已有 glossary，再逐句批量插入
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importGlossaryForArticle(String articleId, GlossaryImportRequest request) {
        // 1. 查询该篇所有句子
        List<Map<String, Object>> sentences = jdbc.queryForList(
                "SELECT id, sort_order FROM article_sentence WHERE article_id = ? ORDER BY sort_order",
                articleId);
        if (sentences.isEmpty()) {
            throw new RuntimeException("名篇不存在或无句子数据: " + articleId);
        }

        // 2. 删除该篇已有 glossary
        jdbc.update("DELETE FROM article_glossary WHERE article_sentence_id IN " +
                "(SELECT id FROM article_sentence WHERE article_id = ?)", articleId);

        // 3. 逐句插入
        String glossarySql = "INSERT INTO article_glossary (article_sentence_id, word, definition, sort_order) " +
                "VALUES (?, ?, ?, ?)";
        int sentenceCount = 0;
        int glossaryCount = 0;
        Map<Integer, Long> sortOrderToId = new HashMap<>();
        for (Map<String, Object> row : sentences) {
            sortOrderToId.put(((Number) row.get("sort_order")).intValue(), ((Number) row.get("id")).longValue());
        }

        if (CollUtil.isNotEmpty(request.getSentences())) {
            for (SentenceGlossary sg : request.getSentences()) {
                Long sentenceId = sortOrderToId.get(sg.getSentenceIndex());
                if (sentenceId == null) continue;
                if (CollUtil.isEmpty(sg.getGlossary())) continue;
                sentenceCount++;

                List<Object[]> gBatch = new ArrayList<>();
                for (int j = 0; j < sg.getGlossary().size(); j++) {
                    GlossaryItem g = sg.getGlossary().get(j);
                    gBatch.add(new Object[]{sentenceId, g.getWord(), g.getDefinition(), j});
                }
                jdbc.batchUpdate(glossarySql, gBatch);
                glossaryCount += gBatch.size();
            }
        }

        log.info("典故注释导入完成: articleId={}, 句子数={}, 词条数={}", articleId, sentenceCount, glossaryCount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("articleId", articleId);
        result.put("sentenceCount", sentenceCount);
        result.put("glossaryCount", glossaryCount);
        return result;
    }

    /**
     * 单本词书独立导入（幂等：先删后插）
     * 只影响该词书及下属的字词/义项/考题等关联数据，不影响名篇、勋章、经典等其他数据
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> importWordBook(String wordBookId, SourceWordBook book) {
        // 1. 删除该词书已有数据（按外键依赖逆序）
        deleteWordBookData(wordBookId);

        // 2. 插入词书元数据（独立导入默认标记为已开通）
        book.setInitialized(true);
        int wordCount = book.getWordEntries() != null ? book.getWordEntries().size() : 0;
        book.setTotalWords(wordCount);
        insertWordBook(book);

        // 3. 插入字词条目
        if (CollUtil.isNotEmpty(book.getWordEntries())) {
            int idx = 0;
            for (SourceWordEntry entry : book.getWordEntries()) {
                insertWordBookEntry(book.getId(), entry, idx++);
            }
        }

        log.info("词书独立导入完成: {} ({} 词, initialized=true)", book.getId(), wordCount);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("wordBookId", wordBookId);
        result.put("name", book.getName());
        result.put("wordCount", wordCount);
        result.put("initialized", true);
        return result;
    }

    // ======================== 删除/清空 ========================

    private void deleteWordBookData(String wordBookId) {
        // 按外键依赖逆序删除
        jdbc.update("DELETE qd FROM quiz_distractor qd " +
                "INNER JOIN quiz_item qi ON qd.quiz_item_id = qi.id " +
                "WHERE qi.entry_id IN (SELECT id FROM word_book_entry WHERE word_book_id = ?)", wordBookId);
        jdbc.update("DELETE FROM quiz_item WHERE entry_id IN " +
                "(SELECT id FROM word_book_entry WHERE word_book_id = ?)", wordBookId);
        jdbc.update("DELETE FROM word_entry_keyword_ref WHERE entry_id IN " +
                "(SELECT id FROM word_book_entry WHERE word_book_id = ?)", wordBookId);
        jdbc.update("DELETE FROM word_usage WHERE entry_id IN " +
                "(SELECT id FROM word_book_entry WHERE word_book_id = ?)", wordBookId);
        jdbc.update("DELETE FROM word_book_entry WHERE word_book_id = ?", wordBookId);
        jdbc.update("DELETE FROM word_book WHERE id = ?", wordBookId);
    }

    private void truncateAll() {
        // 按外键依赖逆序清空（child table → parent table）
        String[] tables = {
                "quiz_distractor",
                "quiz_item",
                "word_entry_keyword_ref",
                "word_usage",
                "word_book_entry",
                "word_book",
                "badge",
                "user_word_progress",
                "user_answer_history",
                "user_checkin",
                "user_badge",
                "study_mistake_sentence",
                "study_mistake",
                "daily_task",
                "feedback",
                "classic_glossary",
                "classic_paragraph",
                "classic_chapter",
                "classic"
        };
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : tables) {
            jdbc.execute("TRUNCATE TABLE " + t);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("已清空 {} 张业务表", tables.length);
    }

    // ======================== 勋章导入 ========================

    private void importBadges(List<SourceBadge> badges) {
        if (CollUtil.isEmpty(badges)) return;
        String sql = "INSERT INTO badge (id, name, description, icon, category, condition_type, condition_value, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        for (SourceBadge b : badges) {
            batch.add(new Object[]{
                    b.getId(), b.getName(), b.getDescription(), b.getIcon(),
                    b.getCategory(),
                    b.getCondition() != null ? b.getCondition().getType() : "streak",
                    b.getCondition() != null ? b.getCondition().getValue() : 0,
                    LocalDateTime.now()
            });
        }
        jdbc.batchUpdate(sql, batch);
        log.info("勋章导入完成: {} 枚", batch.size());
    }

    // ======================== 词书导入 ========================

    private void importWordBooks(List<SourceWordBook> books) {
        if (CollUtil.isEmpty(books)) return;
        for (SourceWordBook book : books) {
            // 幂等：已有词书用 upsert（不碰已有词条），新词书 insert + 有词条才导入
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM word_book WHERE id = ?", Integer.class, book.getId());
            if (existing != null && existing > 0) {
                upsertWordBook(book);
            } else {
                insertWordBook(book);
                if (CollUtil.isNotEmpty(book.getWordEntries())) {
                    int idx = 0;
                    for (SourceWordEntry entry : book.getWordEntries()) {
                        insertWordBookEntry(book.getId(), entry, idx++);
                    }
                }
            }
        }
        log.info("词书导入完成: {} 本", books.size());
    }

    private void insertWordBook(SourceWordBook b) {
        // 防御：从 wordEntries 数组推算元数据，不盲信 JSON 字段值
        int wordCount = b.getWordEntries() != null ? b.getWordEntries().size() : 0;
        boolean init = b.getInitialized() != null ? b.getInitialized() : wordCount > 0;
        if (b.getTotalWords() == null || b.getTotalWords() == 0) {
            b.setTotalWords(wordCount);
        }
        if (wordCount > 0 && !init) {
            b.setInitialized(true);
        }
        jdbc.update(
                "INSERT INTO word_book (id, name, description, category, cover_color, study_mode, identify_prompt, exam_level, initialized, total_words, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                b.getId(), b.getName(), b.getDescription(), b.getCategory(), b.getCoverColor(),
                nvl(b.getStudyMode(), "standard"), b.getIdentifyPrompt(),
                nvl(b.getExamLevel(), "zhongkao"), b.getInitialized() != null ? b.getInitialized() : false,
                b.getTotalWords(), b.getSortOrder() != null ? b.getSortOrder() : 0);
    }

    /** 幂等更新：只更新元数据，不碰已存在的词条数据 */
    private void upsertWordBook(SourceWordBook b) {
        jdbc.update(
                "UPDATE word_book SET name=?, description=?, category=?, cover_color=?, study_mode=?, identify_prompt=?, exam_level=?, sort_order=?, updated_at=NOW() WHERE id=?",
                b.getName(), b.getDescription(), b.getCategory(), b.getCoverColor(),
                nvl(b.getStudyMode(), "standard"), b.getIdentifyPrompt(),
                nvl(b.getExamLevel(), "zhongkao"),
                b.getSortOrder() != null ? b.getSortOrder() : 0,
                b.getId());
    }

    // ======================== 字词条目导入 ========================

    private void insertWordBookEntry(String bookId, SourceWordEntry entry, int sortOrder) {
        // 序列化同音字/形近字列表为 JSON 字符串
        String similarHomophonesJson = CollUtil.isNotEmpty(entry.getSimilarHomophones())
                ? JSONUtil.toJsonStr(entry.getSimilarHomophones()) : null;
        String similarShapesJson = CollUtil.isNotEmpty(entry.getSimilarShapes())
                ? JSONUtil.toJsonStr(entry.getSimilarShapes()) : null;

        jdbc.update(
                "INSERT INTO word_book_entry (id, word_book_id, `character`, pinyin, character_type, explanation, oracle_form, exam_frequency, mnemonic, word_type, similar_homophones, similar_shapes, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.getId(), bookId, entry.getCharacter(), nvl(entry.getPinyin()),
                nvl(entry.getCharacterType()), nvl(entry.getExplanation()),
                nvl(entry.getOracleForm()), nvl(entry.getExamFrequency()),
                nvl(entry.getMnemonic()), nvl(entry.getWordType()),
                similarHomophonesJson, similarShapesJson, sortOrder);

        // 插入关键词引用
        insertKeyWordRefs(entry.getId(), entry.getKeyWordRefs());

        // 插入考题
        if (CollUtil.isNotEmpty(entry.getQuizItems())) {
            for (int i = 0; i < entry.getQuizItems().size(); i++) {
                insertQuizItem(entry.getId(), entry.getQuizItems().get(i), i);
            }
        }

        // 插入用法（只读型词书的释义/例句）
        insertWordUsages(entry.getId(), entry.getUsages());
    }

    private void insertKeyWordRefs(String entryId, List<SourceKeyWordRef> refs) {
        if (CollUtil.isEmpty(refs)) return;
        String sql = "INSERT INTO word_entry_keyword_ref (entry_id, kid, sort_order) VALUES (?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < refs.size(); i++) {
            batch.add(new Object[]{entryId, refs.get(i).getKid(), i});
        }
        jdbc.batchUpdate(sql, batch);
    }

    private void insertQuizItem(String entryId, SourceQuizItem qi, int sortOrder) {
        jdbc.update(
                "INSERT INTO quiz_item (id, entry_id, kid_ref, difficulty, target_word, definition, sort_order, sentence_text, sentence_translation, sentence_source) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                qi.getId(), entryId, qi.getKidRef(), nvl(qi.getDifficulty(), "basic"),
                qi.getTargetWord(), qi.getDefinition(), sortOrder,
                nvl(qi.getSentenceText()), nvl(qi.getSentenceTranslation()), nvl(qi.getSentenceSource()));

        // 批量插入干扰项
        if (CollUtil.isNotEmpty(qi.getDistractors())) {
            String sql = "INSERT INTO quiz_distractor (quiz_item_id, text, sort_order) VALUES (?, ?, ?)";
            List<Object[]> batch = new ArrayList<>();
            for (int i = 0; i < qi.getDistractors().size(); i++) {
                batch.add(new Object[]{qi.getId(), qi.getDistractors().get(i), i});
            }
            jdbc.batchUpdate(sql, batch);
        }
    }

    private void insertWordUsages(String entryId, List<SourceWordUsage> usages) {
        if (CollUtil.isEmpty(usages)) return;
        String sql = "INSERT INTO word_usage (entry_id, usage_type, definition, example_sentence, example_translation, example_source, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < usages.size(); i++) {
            SourceWordUsage u = usages.get(i);
            batch.add(new Object[]{
                    entryId, u.getUsageType(), u.getDefinition(),
                    u.getExampleSentence(), u.getExampleTranslation(), u.getExampleSource(), i
            });
        }
        jdbc.batchUpdate(sql, batch);
    }

    // ======================== 名篇导入 ========================

    private void importArticles(List<SourceArticle> articles) {
        if (CollUtil.isEmpty(articles)) return;
        String articleSql = "INSERT INTO article (id, title, author, dynasty, category, textbook, background, full_text_audio_url, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String sentenceSql = "INSERT INTO article_sentence (article_id, text, translation, audio_url, sort_order) " +
                "VALUES (?, ?, ?, ?, ?)";
        String keywordSql = "INSERT INTO article_keyword (article_sentence_id, word_text, definition, word_book_id, mastery_level, kid, match_word, word_type, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String annotationSql = "INSERT INTO article_char_annotation (article_sentence_id, char_text, `role`, definition, sort_order) " +
                "VALUES (?, ?, ?, ?, ?)";
        String glossarySql = "INSERT INTO article_glossary (article_sentence_id, word, definition, sort_order) " +
                "VALUES (?, ?, ?, ?)";

        for (SourceArticle a : articles) {
            int sortOrder;
            String artId = a.getId();
            if (artId.startsWith("art_shell_")) {
                // 壳文章 offset 10000 起
                sortOrder = 10000 + Integer.parseInt(artId.replace("art_shell_", ""));
            } else {
                sortOrder = Integer.parseInt(artId.replace("art_", ""));
            }
            jdbc.update(articleSql, a.getId(), a.getTitle(), nvl(a.getAuthor()), nvl(a.getDynasty()),
                    nvl(a.getCategory(), "prose"), a.getTextbook(), a.getBackground(), a.getFullTextAudioUrl(),
                    sortOrder);

            if (CollUtil.isEmpty(a.getSentences())) continue;

            for (int i = 0; i < a.getSentences().size(); i++) {
                SourceArticleSentence s = a.getSentences().get(i);
                jdbc.update(sentenceSql, a.getId(), s.getText(), nvl(s.getTranslation()), s.getAudioUrl(), i);
                Long sentenceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                if (CollUtil.isNotEmpty(s.getKeyWords())) {
                    List<Object[]> kwBatch = s.getKeyWords().stream()
                            .map(kw -> new Object[]{sentenceId, kw.getWord(), kw.getDefinition(),
                                    kw.getWordBookId(), kw.getMasteryLevel(), kw.getKid(),
                                    kw.getMatchWord(), kw.getWordType(), 0})
                            .collect(Collectors.toList());
                    jdbc.batchUpdate(keywordSql, kwBatch);
                }

                if (CollUtil.isNotEmpty(s.getCharAnnotations())) {
                    List<Object[]> caBatch = new ArrayList<>();
                    for (int j = 0; j < s.getCharAnnotations().size(); j++) {
                        SourceCharAnnotation ca = s.getCharAnnotations().get(j);
                        caBatch.add(new Object[]{sentenceId, ca.getCharText(), ca.getRole(), ca.getDefinition(), j});
                    }
                    jdbc.batchUpdate(annotationSql, caBatch);
                }

                if (CollUtil.isNotEmpty(s.getGlossary())) {
                    List<Object[]> gBatch = new ArrayList<>();
                    for (int j = 0; j < s.getGlossary().size(); j++) {
                        SourceGlossaryItem g = s.getGlossary().get(j);
                        gBatch.add(new Object[]{sentenceId, g.getWord(), g.getDefinition(), j});
                    }
                    jdbc.batchUpdate(glossarySql, gBatch);
                }
            }
        }
        log.info("名篇导入完成: {} 篇", articles.size());
    }

    // ======================== 经典导入 ========================

    private void importClassics(List<SourceClassic> classics) {
        if (CollUtil.isEmpty(classics)) return;

        // 幂等：已有经典只更新元数据，不碰章节内容；新经典正常插入
        for (SourceClassic c : classics) {
            Integer existing = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM classic WHERE id = ?", Integer.class, c.getId());
            if (existing != null && existing > 0) {
                jdbc.update(
                        "UPDATE classic SET name=?, era=?, author=?, icon=?, description=?, category=?, structure_type=?, load_mode=?, nav_mode=?, sort_order=?, updated_at=NOW() WHERE id=?",
                        c.getName(), nvl(c.getEra()), nvl(c.getAuthor()), nvl(c.getIcon()), nvl(c.getDescription()),
                        nvl(c.getCategory()), nvl(c.getStructureType(), "chapter"),
                        nvl(c.getLoadMode(), "chunked"), nvl(c.getNavMode(), "list"),
                        c.getSortOrder() != null ? c.getSortOrder() : 0, c.getId());
            } else {
                jdbc.update(
                        "INSERT INTO classic (id, name, era, author, icon, description, category, structure_type, load_mode, nav_mode, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        c.getId(), c.getName(), nvl(c.getEra()), nvl(c.getAuthor()),
                        nvl(c.getIcon()), nvl(c.getDescription()), nvl(c.getCategory()),
                        nvl(c.getStructureType(), "chapter"),
                        nvl(c.getLoadMode(), "chunked"),
                        nvl(c.getNavMode(), "list"),
                        c.getSortOrder() != null ? c.getSortOrder() : 0);
            }
        }
        log.info("经典著作元数据导入完成: {} 部（幂等 upsert）", classics.size());
    }

    /**
     * 导入一部经典著作的章节内容（含章节、段落、注释）
     * 幂等：先删除该经典下已有的所有章节/段落/注释数据，再重新插入
     * 支持两种数据格式：
     *   - 章节型（chapters 含 paragraphs 直接段落）-> 一级 chapter 结构
     *   - 选集型（chapters 含 entries，entries 含 paragraphs）-> 二级 chapter(parent->child) 结构
     * @param classicId 经典著作 ID（classic 表主键）
     * @param chapters  章节/门类数组（来自 chapters.json 或 entries.json）
     */
    @Transactional(rollbackFor = Exception.class)
    public void importClassicBook(Long classicId, List<SourceClassicChapter> chapters) {
        // 1. 确认经典著作元数据存在
        Classic classic = classicMapper.selectById(classicId);
        String classicName = classic != null ? classic.getName() : "ID=" + classicId;

        // 2. 检测数据格式：有 entries 字段 -> 选集型（二级结构）
        boolean isAnthology = !CollUtil.isEmpty(chapters)
                && chapters.stream().anyMatch(ch -> !CollUtil.isEmpty(ch.getEntries()));

        // 3. 删除该经典下已有的章节/段落/注释（幂等）
        deleteClassicData(classicId);

        // 4. 批量插入
        if (CollUtil.isEmpty(chapters)) {
            log.info("经典「{}」无章节数据，已清空旧数据", classicName);
            return;
        }

        if (isAnthology) {
            importAnthologyData(classicId, chapters, classicName);
        } else {
            importChapterData(classicId, chapters, classicName);
        }
    }

    private void deleteClassicData(Long classicId) {
        List<Long> existingChapterIds = jdbc.queryForList(
                "SELECT id FROM classic_chapter WHERE classic_id = ?", Long.class, classicId);
        if (!existingChapterIds.isEmpty()) {
            String inClause = existingChapterIds.stream().map(String::valueOf).collect(Collectors.joining(","));
            List<Long> existingParaIds = jdbc.queryForList(
                    "SELECT id FROM classic_paragraph WHERE chapter_id IN (" + inClause + ")", Long.class);
            if (!existingParaIds.isEmpty()) {
                String paraIn = existingParaIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                jdbc.update("DELETE FROM classic_glossary WHERE paragraph_id IN (" + paraIn + ")");
            }
            jdbc.update("DELETE FROM classic_paragraph WHERE chapter_id IN (" + inClause + ")");
        }
        jdbc.update("DELETE FROM classic_chapter WHERE classic_id = ?", classicId);
    }

    /** 选集型导入：门->条目 二级结构 */
    private void importAnthologyData(Long classicId, List<SourceClassicChapter> groups, String classicName) {
        String chapterSql = "INSERT INTO classic_chapter (classic_id, parent_id, title, author, era, background, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        int groupCount = 0;
        int entryCount = 0;
        int totalParagraphs = 0;

        for (int gi = 0; gi < groups.size(); gi++) {
            SourceClassicChapter group = groups.get(gi);
            // 插入门类（parent_id = null，author/era 为 null）
            jdbc.update(chapterSql, classicId, null, group.getTitle(), null, null, null, gi, now, now);
            Long parentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            groupCount++;

            if (CollUtil.isEmpty(group.getEntries())) continue;

            for (int ei = 0; ei < group.getEntries().size(); ei++) {
                SourceAnthologyEntry entry = group.getEntries().get(ei);
                // 插入条目（parent_id = 门类 ID，author 继承父 group 标题，era 来自 entry JSON）
                String entryAuthor = (entry.getAuthor() != null && !entry.getAuthor().isEmpty())
                        ? entry.getAuthor() : group.getTitle();
                String entryEra = (entry.getEra() != null && !entry.getEra().isEmpty())
                        ? entry.getEra() : null;
                jdbc.update(chapterSql, classicId, parentId, entry.getTitle(),
                        entryAuthor, entryEra, entry.getBackground(), ei, now, now);
                Long entryChapterId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
                entryCount++;

                // 插入段落到条目下
                if (!CollUtil.isEmpty(entry.getParagraphs())) {
                    totalParagraphs += insertParagraphs(entryChapterId, entry.getParagraphs(), now);
                }
            }
        }

        log.info("经典「{}」(选集型) 导入完成: {} 门, {} 条, {} 段落", classicName, groupCount, entryCount, totalParagraphs);
    }

    /** 章节型导入：一级 chapter 结构（旧格式兼容） */
    private void importChapterData(Long classicId, List<SourceClassicChapter> chapters, String classicName) {
        String chapterSql = "INSERT INTO classic_chapter (classic_id, parent_id, title, author, era, background, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        LocalDateTime now = LocalDateTime.now();
        int totalParagraphs = 0;

        for (int ci = 0; ci < chapters.size(); ci++) {
            SourceClassicChapter chapter = chapters.get(ci);
            jdbc.update(chapterSql, classicId, null, chapter.getTitle(), null, null, null, ci, now, now);
            Long chapterId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            if (!CollUtil.isEmpty(chapter.getParagraphs())) {
                totalParagraphs += insertParagraphs(chapterId, chapter.getParagraphs(), now);
            }
        }

        log.info("经典「{}」(章节型) 导入完成: {} 章, {} 段落", classicName, chapters.size(), totalParagraphs);
    }

    /** 插入段落 + 注释，返回段落数 */
    private int insertParagraphs(Long chapterId, List<SourceClassicParagraph> paragraphs, LocalDateTime now) {
        String paragraphSql = "INSERT INTO classic_paragraph (chapter_id, sort_order, text, translation, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String glossarySql = "INSERT INTO classic_glossary (paragraph_id, word, explanation, sort_order, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        int count = 0;

        for (int pi = 0; pi < paragraphs.size(); pi++) {
            SourceClassicParagraph para = paragraphs.get(pi);
            jdbc.update(paragraphSql, chapterId, pi, para.getText(),
                    para.getTranslation() != null ? para.getTranslation() : "", now, now);
            Long paragraphId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            count++;

            if (!CollUtil.isEmpty(para.getGlossary())) {
                for (int gi = 0; gi < para.getGlossary().size(); gi++) {
                    SourceClassicGlossary gloss = para.getGlossary().get(gi);
                    jdbc.update(glossarySql, paragraphId, gloss.getWord(), gloss.getExplanation(), gi, now, now);
                }
            }
        }
        return count;
    }

    // ======================== 工具方法 ========================

    private void insertStrings(String sql, String parentId, List<String> values) {
        List<Object[]> batch = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            batch.add(new Object[]{parentId, values.get(i), i});
        }
        jdbc.batchUpdate(sql, batch);
    }

    private static String nvl(String s) { return s == null ? "" : s; }
    private static String nvl(String s, String def) { return s == null || s.isEmpty() ? def : s; }
}
