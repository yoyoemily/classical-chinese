package com.bogutongjin.service;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.bogutongjin.dto.SourceData;
import com.bogutongjin.dto.SourceData.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional(rollbackFor = Exception.class)
    public void importFromJson(String jsonPath) {
        String json = FileUtil.readString(jsonPath, StandardCharsets.UTF_8);
        SourceData source = JSONUtil.toBean(json, SourceData.class);
        log.info("数据源解析完成: {} 本词书, {} 篇名篇, {} 枚勋章",
                source.getWordBooks().size(),
                source.getArticles() != null ? source.getArticles().size() : 0,
                source.getBadges() != null ? source.getBadges().size() : 0);

        truncateAll();
        importBadges(source.getBadges());
        importWordBooks(source.getWordBooks());
        importArticles(source.getArticles());
        importArticleRelatedWords(source.getArticles());

        log.info("数据源导入完成");
    }

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

    private void importWordBooks(List<SourceWordBook> books) {
        if (CollUtil.isEmpty(books)) return;
        for (SourceWordBook book : books) {
            insertWordBook(book);
            if (CollUtil.isNotEmpty(book.getWords())) {
                for (SourceWord w : book.getWords()) {
                    insertWord(book.getId(), w);
                }
            }
        }
        log.info("词书导入完成: {} 本", books.size());
    }

    private void insertWordBook(SourceWordBook b) {
        jdbc.update(
                "INSERT INTO word_book (id, name, description, category, cover_color, total_words, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)",
                b.getId(), b.getName(), b.getDescription(), b.getCategory(), b.getCoverColor(), b.getTotalWords(), 0);
    }

    private void insertWord(String bookId, SourceWord w) {
        jdbc.update(
                "INSERT INTO word (id, word_book_id, `character`, pinyin, character_type, explanation, oracle_form, exam_frequency, mnemonic, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                w.getId(), bookId, w.getCharacter(), nvl(w.getPinyin()), nvl(w.getCharacterType()),
                nvl(w.getExplanation()), nvl(w.getOracleForm()), nvl(w.getExamFrequency()),
                nvl(w.getMnemonic()), 0);

        if (CollUtil.isNotEmpty(w.getMeanings())) {
            String sql = "INSERT INTO meaning (word_id, definition, pinyin, example, translation, source, sort_order) VALUES (?, ?, ?, ?, ?, ?, ?)";
            List<Object[]> batch = new ArrayList<>();
            for (int i = 0; i < w.getMeanings().size(); i++) {
                SourceMeaning m = w.getMeanings().get(i);
                batch.add(new Object[]{w.getId(), m.getDefinition(), nvl(m.getPinyin()), m.getExample(), nvl(m.getTranslation()), nvl(m.getSource()), i});
            }
            jdbc.batchUpdate(sql, batch);
        }

        if (CollUtil.isNotEmpty(w.getSentences())) {
            for (SourceSentence s : w.getSentences()) {
                insertSentence(w.getId(), s);
            }
        }

        if (CollUtil.isNotEmpty(w.getSimilarHomophones())) {
            insertStrings("INSERT INTO similar_homophone (word_id, `character`, sort_order) VALUES (?, ?, ?)",
                    w.getId(), w.getSimilarHomophones());
        }

        if (CollUtil.isNotEmpty(w.getSimilarShapes())) {
            insertStrings("INSERT INTO similar_shape (word_id, `character`, sort_order) VALUES (?, ?, ?)",
                    w.getId(), w.getSimilarShapes());
        }
    }

    private void insertSentence(String wordId, SourceSentence s) {
        jdbc.update(
                "INSERT INTO sentence (id, word_id, text, source, translation, target_word, correct_meaning_index, difficulty, full_text, article_id, audio_url, sort_order) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                s.getId(), wordId, s.getText(), nvl(s.getSource()), nvl(s.getTranslation()), nvl(s.getTargetWord()),
                s.getCorrectMeaningIndex() != null ? s.getCorrectMeaningIndex() : 0,
                nvl(s.getDifficulty(), "basic"),
                s.getFullText(), s.getArticleId(), s.getAudioUrl(), 0);

        if (CollUtil.isNotEmpty(s.getDistractors())) {
            insertStrings("INSERT INTO sentence_distractor (sentence_id, text, sort_order) VALUES (?, ?, ?)",
                    s.getId(), s.getDistractors());
        }
    }

    private void importArticles(List<SourceArticle> articles) {
        if (CollUtil.isEmpty(articles)) return;
        String articleSql = "INSERT INTO article (id, title, author, dynasty, category, textbook, full_text_audio_url, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sentenceSql = "INSERT INTO article_sentence (article_id, text, translation, audio_url, sort_order) " +
                "VALUES (?, ?, ?, ?, ?)";
        String keywordSql = "INSERT INTO article_keyword (article_sentence_id, word_text, definition, word_book_id, mastery_level, sort_order) " +
                "VALUES (?, ?, ?, ?, ?, ?)";
        String annotationSql = "INSERT INTO article_char_annotation (article_sentence_id, char_text, role, definition, sort_order) " +
                "VALUES (?, ?, ?, ?, ?)";

        for (SourceArticle a : articles) {
            jdbc.update(articleSql, a.getId(), a.getTitle(), nvl(a.getAuthor()), nvl(a.getDynasty()),
                    nvl(a.getCategory(), "prose"), a.getTextbook(), a.getFullTextAudioUrl(),
                    Integer.parseInt(a.getId().replace("art_", "")));

            if (CollUtil.isEmpty(a.getSentences())) continue;

            for (int i = 0; i < a.getSentences().size(); i++) {
                SourceArticleSentence s = a.getSentences().get(i);
                jdbc.update(sentenceSql, a.getId(), s.getText(), nvl(s.getTranslation()), s.getAudioUrl(), i);
                Long sentenceId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

                if (CollUtil.isNotEmpty(s.getKeyWords())) {
                    List<Object[]> kwBatch = s.getKeyWords().stream()
                            .map(kw -> new Object[]{sentenceId, kw.getWord(), kw.getDefinition(),
                                    kw.getWordBookId(), kw.getMasteryLevel(), 0})
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
            }
        }
        log.info("名篇导入完成: {} 篇", articles.size());
    }

    private void importArticleRelatedWords(List<SourceArticle> articles) {
        if (CollUtil.isEmpty(articles)) return;
        String sql = "INSERT IGNORE INTO article_related_word (article_id, word_id) VALUES (?, ?)";
        List<Object[]> batch = new ArrayList<>();
        for (SourceArticle a : articles) {
            if (CollUtil.isNotEmpty(a.getRelatedWordIds())) {
                for (String wid : a.getRelatedWordIds()) {
                    batch.add(new Object[]{a.getId(), wid});
                }
            }
        }
        if (!batch.isEmpty()) {
            jdbc.batchUpdate(sql, batch);
        }
        log.info("名篇-字词关联导入完成: {} 条", batch.size());
    }

    private void truncateAll() {
        String[] tables = {
                "article_related_word", "article_char_annotation", "article_keyword",
                "article_sentence", "article",
                "sentence_distractor", "sentence", "meaning",
                "similar_homophone", "similar_shape",
                "word", "word_book", "badge"
        };
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String t : tables) {
            jdbc.execute("TRUNCATE TABLE " + t);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("已清空 {} 张业务表", tables.length);
    }

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
