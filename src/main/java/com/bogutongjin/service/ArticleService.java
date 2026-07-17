package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.common.ResourceNotFoundException;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import com.bogutongjin.util.PinyinUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleSentenceMapper articleSentenceMapper;
    private final ArticleKeywordMapper articleKeywordMapper;
    private final ArticleGlossaryMapper articleGlossaryMapper;
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> getArticles(String category, String textbook) {
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>()
                .eq(Article::getHasContent, 1)
                .orderByAsc(Article::getSortOrder);

        if (category != null && !"undefined".equals(category) && !"all".equals(category)) {
            qw.eq(Article::getCategory, category);
        }
        if (textbook != null && !"undefined".equals(textbook) && !"all".equals(textbook)) {
            if ("other".equals(textbook)) {
                qw.isNull(Article::getTextbook);
            } else {
                qw.eq(Article::getTextbook, textbook);
            }
        }

        List<Article> articles = articleMapper.selectList(qw);

        // 批量查询每篇文章的词书重点字数（article_sentence JOIN article_keyword）
        List<String> articleIds = articles.stream().map(Article::getId).toList();
        Map<String, Integer> keywordCountMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            String inClause = articleIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(","));
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT s.article_id, COUNT(k.id) AS cnt FROM article_sentence s " +
                    "INNER JOIN article_keyword k ON k.article_sentence_id = s.id " +
                    "WHERE s.article_id IN (" + inClause + ") GROUP BY s.article_id");
            for (Map<String, Object> row : rows) {
                keywordCountMap.put((String) row.get("article_id"),
                        ((Number) row.get("cnt")).intValue());
            }
        }

        return articles.stream().map(a -> toArticleListMap(a, keywordCountMap.getOrDefault(a.getId(), 0)))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toArticleListMap(Article a, int keywordCount) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.getId());
        result.put("title", a.getTitle());
        result.put("author", a.getAuthor());
        result.put("dynasty", a.getDynasty());
        result.put("category", a.getCategory());
        result.put("textbook", a.getTextbook());
        result.put("background", a.getBackground());
        result.put("fullTextAudioUrl", a.getFullTextAudioUrl());
        result.put("sentences", new ArrayList<>());
        result.put("keywordCount", keywordCount);
        return result;
    }

    public Map<String, Object> getArticleDetail(String articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null) throw new ResourceNotFoundException("名篇不存在");
        return toArticleMap(article);
    }

    private Map<String, Object> toArticleMap(Article a) {
        // 句子
        List<ArticleSentence> sentences = articleSentenceMapper.selectList(
                new LambdaQueryWrapper<ArticleSentence>().eq(ArticleSentence::getArticleId, a.getId())
                        .orderByAsc(ArticleSentence::getSortOrder));

        List<Map<String, Object>> sentenceList = sentences.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("text", s.getText());
            sm.put("translation", s.getTranslation());
            sm.put("audioUrl", s.getAudioUrl());

            // 内联生词
            List<ArticleKeyword> keywords = articleKeywordMapper.selectList(
                    new LambdaQueryWrapper<ArticleKeyword>()
                            .eq(ArticleKeyword::getArticleSentenceId, s.getId())
                            .orderByAsc(ArticleKeyword::getSortOrder));
            sm.put("keyWords", keywords.stream().map(kw -> {
                Map<String, Object> km = new LinkedHashMap<>();
                km.put("word", kw.getWordText());
                km.put("definition", kw.getDefinition());
                km.put("wordBookId", kw.getWordBookId());
                km.put("masteryLevel", kw.getMasteryLevel());
                if (kw.getKid() != null) {
                    km.put("kid", kw.getKid());
                }
                if (kw.getMatchWord() != null) {
                    km.put("matchWord", kw.getMatchWord());
                }
                if (kw.getWordType() != null) {
                    km.put("wordType", kw.getWordType());
                }
                return km;
            }).collect(Collectors.toList()));

            // 典故注释
            List<ArticleGlossary> glossaryList = articleGlossaryMapper.selectList(
                    new LambdaQueryWrapper<ArticleGlossary>()
                            .eq(ArticleGlossary::getArticleSentenceId, s.getId())
                            .orderByAsc(ArticleGlossary::getSortOrder));
            sm.put("glossary", glossaryList.stream().map(g -> {
                Map<String, Object> gm = new LinkedHashMap<>();
                gm.put("word", g.getWord());
                gm.put("definition", g.getDefinition());
                return gm;
            }).collect(Collectors.toList()));

            // 生僻字拼音
            sm.put("rareCharPinyin", PinyinUtils.buildRareCharPinyin(s.getText()));

            return sm;
        }).collect(Collectors.toList());

        // 词书重点字数
        int keywordCount = sentenceList.stream()
                .mapToInt(s -> ((List<?>) s.get("keyWords")).size())
                .sum();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.getId());
        result.put("title", a.getTitle());
        result.put("author", a.getAuthor());
        result.put("dynasty", a.getDynasty());
        result.put("category", a.getCategory());
        result.put("textbook", a.getTextbook());
        result.put("background", a.getBackground());
        result.put("fullTextAudioUrl", a.getFullTextAudioUrl());
        result.put("sentences", sentenceList);
        result.put("keywordCount", keywordCount);
        return result;
    }
}
