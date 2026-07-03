package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.common.ResourceNotFoundException;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleSentenceMapper articleSentenceMapper;
    private final ArticleKeywordMapper articleKeywordMapper;
    private final ArticleCharAnnotationMapper articleCharAnnotationMapper;
    private final ArticleRelatedWordMapper articleRelatedWordMapper;

    public List<Map<String, Object>> getArticles(String category, String textbook) {
        LambdaQueryWrapper<Article> qw = new LambdaQueryWrapper<Article>().orderByAsc(Article::getSortOrder);

        if (category != null && !"undefined".equals(category) && !"all".equals(category)) {
            qw.eq(Article::getCategory, category);
        }
        if (textbook != null && !"undefined".equals(textbook) && !"all".equals(textbook)) {
            qw.eq(Article::getTextbook, textbook);
        }

        List<Article> articles = articleMapper.selectList(qw);
        return articles.stream().map(this::toArticleMap).collect(Collectors.toList());
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
                return km;
            }).collect(Collectors.toList()));

            // 逐字标注
            List<ArticleCharAnnotation> annotations = articleCharAnnotationMapper.selectList(
                    new LambdaQueryWrapper<ArticleCharAnnotation>()
                            .eq(ArticleCharAnnotation::getArticleSentenceId, s.getId())
                            .orderByAsc(ArticleCharAnnotation::getSortOrder));
            sm.put("charAnnotations", annotations.stream().map(ca -> {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("char", ca.getCharText());
                cm.put("role", ca.getRole());
                cm.put("definition", ca.getDefinition());
                return cm;
            }).collect(Collectors.toList()));

            return sm;
        }).collect(Collectors.toList());

        // 关联字词
        List<ArticleRelatedWord> relatedWords = articleRelatedWordMapper.selectList(
                new LambdaQueryWrapper<ArticleRelatedWord>().eq(ArticleRelatedWord::getArticleId, a.getId()));
        List<String> relatedWordIds = relatedWords.stream().map(ArticleRelatedWord::getWordId).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", a.getId());
        result.put("title", a.getTitle());
        result.put("author", a.getAuthor());
        result.put("dynasty", a.getDynasty());
        result.put("category", a.getCategory());
        result.put("textbook", a.getTextbook());
        result.put("fullTextAudioUrl", a.getFullTextAudioUrl());
        result.put("sentences", sentenceList);
        result.put("relatedWordIds", relatedWordIds);
        return result;
    }
}
