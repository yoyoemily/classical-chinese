package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ContentService {

    private final WordBookEntryMapper wordBookEntryMapper;
    private final QuizItemMapper quizItemMapper;
    private final QuizDistractorMapper quizDistractorMapper;
    private final WordBookMapper wordBookMapper;
    private final ArticleKeywordMapper articleKeywordMapper;
    private final ArticleSentenceMapper articleSentenceMapper;

    public Map<String, Object> getWordDetail(String entryId) {
        WordBookEntry entry = wordBookEntryMapper.selectById(entryId);
        if (entry == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", entry.getId());
        result.put("character", entry.getCharacter());
        result.put("pinyin", entry.getPinyin());
        result.put("characterType", entry.getCharacterType());
        result.put("explanation", entry.getExplanation());
        result.put("examFrequency", entry.getExamFrequency());
        result.put("mnemonic", entry.getMnemonic());
        result.put("wordType", entry.getWordType());

        // 义项列表以 quizItem 驱动：每个 quizItem 即一个义项，keyWordRef 只补 word / articleId
        List<QuizItem> quizItems = quizItemMapper.selectList(
                new LambdaQueryWrapper<QuizItem>().eq(QuizItem::getEntryId, entryId).orderByAsc(QuizItem::getSortOrder));
        result.put("keyWordRefs", quizItems.stream().map(qi -> {
            Map<String, Object> rm = new LinkedHashMap<>();
            rm.put("kid", qi.getKidRef() != null ? qi.getKidRef() : "");
            rm.put("definition", qi.getDefinition() != null ? qi.getDefinition() : "");
            rm.put("sentenceText", qi.getSentenceText() != null ? qi.getSentenceText() : "");
            rm.put("sentenceTranslation", qi.getSentenceTranslation() != null ? qi.getSentenceTranslation() : "");
            rm.put("articleTitle", qi.getSentenceSource() != null ? qi.getSentenceSource() : "");

            // 从 article_keyword 补 word 和 articleId
            if (qi.getKidRef() != null && !qi.getKidRef().isEmpty()) {
                ArticleKeyword ak = articleKeywordMapper.selectOne(
                        new LambdaQueryWrapper<ArticleKeyword>().eq(ArticleKeyword::getKid, qi.getKidRef()));
                if (ak != null) {
                    rm.put("word", ak.getWordText() != null ? ak.getWordText() : "");
                    ArticleSentence as = articleSentenceMapper.selectById(ak.getArticleSentenceId());
                    if (as != null) {
                        rm.put("articleId", as.getArticleId() != null ? as.getArticleId() : "");
                    }
                }
            }
            return rm;
        }).collect(Collectors.toList()));

        // Quiz 题目（含干扰项），直接复用上文的 quizItems 列表
        result.put("quizItems", quizItems.stream().map(q -> {
            Map<String, Object> qm = new LinkedHashMap<>();
            qm.put("id", q.getId());
            qm.put("definition", q.getDefinition());
            qm.put("difficulty", q.getDifficulty());
            qm.put("targetWord", q.getTargetWord());
            qm.put("kidRef", q.getKidRef());
            List<QuizDistractor> distractors = quizDistractorMapper.selectList(
                    new LambdaQueryWrapper<QuizDistractor>().eq(QuizDistractor::getQuizItemId, q.getId())
                            .orderByAsc(QuizDistractor::getSortOrder));
            qm.put("distractors", distractors.stream().map(QuizDistractor::getText).collect(Collectors.toList()));
            return qm;
        }).collect(Collectors.toList()));

        // 同音易混和形近字现在作为 JSON 字段存在 WordBookEntry 中
        result.put("similarHomophones", parseJsonArray(entry.getSimilarHomophones()));
        result.put("similarShapes", parseJsonArray(entry.getSimilarShapes()));

        return result;
    }

    /** 全局搜索字词 */
    public List<Map<String, Object>> searchWords(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();

        List<WordBookEntry> entries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().like(WordBookEntry::getCharacter, keyword));

        List<Map<String, Object>> result = new ArrayList<>();
        for (WordBookEntry entry : entries) {
            // 跳过只读类词书（虚词深度解析等）
            WordBook book = wordBookMapper.selectById(entry.getWordBookId());
            if (book == null || "readonly".equals(book.getStudyMode())) continue;

            // 获取所有 quiz item 定义作为义项
            List<QuizItem> quizItems = quizItemMapper.selectList(
                    new LambdaQueryWrapper<QuizItem>().eq(QuizItem::getEntryId, entry.getId())
                            .orderByAsc(QuizItem::getSortOrder));

            List<Map<String, Object>> meaningList = new ArrayList<>();
            for (QuizItem q : quizItems) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("definition", q.getDefinition());
                mm.put("difficulty", q.getDifficulty());
                meaningList.add(mm);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", entry.getId());
            item.put("character", entry.getCharacter());
            item.put("pinyin", entry.getPinyin());
            item.put("meanings", meaningList);
            item.put("wordBookName", book.getName());
            item.put("wordBookId", entry.getWordBookId());
            result.add(item);
        }
        return result;
    }

    /** 按词类分组返回所有已初始化词书中的字词（供快捷搜索使用） */
    public Map<String, List<Map<String, Object>>> getWordsByType() {
        // 1. 查出所有已初始化的词书（排除只读类词书）
        List<WordBook> initializedBooks = wordBookMapper.selectList(
                new LambdaQueryWrapper<WordBook>()
                        .eq(WordBook::getInitialized, true)
                        .ne(WordBook::getStudyMode, "readonly"));
        if (initializedBooks.isEmpty()) return Map.of();

        List<String> bookIds = initializedBooks.stream()
                .map(WordBook::getId).collect(Collectors.toList());

        // 2. 批量查出所有 word_book_entry
        List<WordBookEntry> entries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().in(WordBookEntry::getWordBookId, bookIds)
                        .orderByAsc(WordBookEntry::getSortOrder));

        // 3. 按 wordType 分组，shi/xu 合并为 shixu
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("shixu", new ArrayList<>());
        result.put("tongjia", new ArrayList<>());
        result.put("huoyong", new ArrayList<>());
        result.put("gujinyi", new ArrayList<>());

        for (WordBookEntry entry : entries) {
            String key = resolveGroupKey(entry.getWordType());
            if (key == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("entryId", entry.getId());
            item.put("character", entry.getCharacter());
            item.put("pinyin", entry.getPinyin());
            result.get(key).add(item);
        }

        return result;
    }

    private String resolveGroupKey(String wordType) {
        if (wordType == null) return null;
        return switch (wordType) {
            case "shi", "xu" -> "shixu";
            case "tongjia" -> "tongjia";
            case "huoyong" -> "huoyong";
            case "gujinyi" -> "gujinyi";
            default -> null;
        };
    }

    /** 简单解析 JSON 字符串数组 ["a","b"] → List<String>，失败返回空列表 */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.trim().isEmpty()) return List.of();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }
}
