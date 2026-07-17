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
public class WordBookService {

    private final WordBookMapper wordBookMapper;
    private final WordBookEntryMapper wordBookEntryMapper;
    private final WordEntryKeywordRefMapper wordEntryKeywordRefMapper;
    private final QuizItemMapper quizItemMapper;
    private final QuizDistractorMapper quizDistractorMapper;
    private final WordUsageMapper wordUsageMapper;

    /** 获取词书列表（摘要） */
    public List<Map<String, Object>> getWordBooks() {
        return wordBookMapper.selectList(
                new LambdaQueryWrapper<WordBook>().orderByAsc(WordBook::getSortOrder)).stream()
                .map(b -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", b.getId());
                    map.put("name", b.getName());
                    map.put("description", b.getDescription());
                    map.put("category", b.getCategory());
                    map.put("coverColor", b.getCoverColor());
                    map.put("studyMode", b.getStudyMode());
                    map.put("identifyPrompt", b.getIdentifyPrompt());
                    map.put("examLevel", b.getExamLevel());
                    map.put("initialized", b.getInitialized());
                    map.put("totalWords", b.getTotalWords());
                    return map;
                }).collect(Collectors.toList());
    }

    /** 获取词书详情（含所有字词、关键词引用、quiz 题目、用法） */
    public Map<String, Object> getWordBookDetail(String bookId) {
        WordBook book = wordBookMapper.selectById(bookId);
        if (book == null) return null;

        List<WordBookEntry> entries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().eq(WordBookEntry::getWordBookId, bookId).orderByAsc(WordBookEntry::getSortOrder));

        List<Map<String, Object>> wordList = entries.stream().map(e -> {
            Map<String, Object> em = new LinkedHashMap<>();
            em.put("id", e.getId());
            em.put("character", e.getCharacter());
            em.put("pinyin", e.getPinyin());
            em.put("characterType", e.getCharacterType());
            em.put("explanation", e.getExplanation());
            em.put("oracleForm", e.getOracleForm());
            em.put("examFrequency", e.getExamFrequency());
            em.put("mnemonic", e.getMnemonic());
            em.put("wordType", e.getWordType());
            em.put("similarHomophones", parseJsonArray(e.getSimilarHomophones()));
            em.put("similarShapes", parseJsonArray(e.getSimilarShapes()));

            // 关键词引用
            List<WordEntryKeywordRef> keyWordRefs = wordEntryKeywordRefMapper.selectList(
                    new LambdaQueryWrapper<WordEntryKeywordRef>().eq(WordEntryKeywordRef::getEntryId, e.getId()).orderByAsc(WordEntryKeywordRef::getSortOrder));
            em.put("keyWordRefs", keyWordRefs.stream().map(r -> {
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("kid", r.getKid());
                return rm;
            }).collect(Collectors.toList()));

            // Quiz 题目（含干扰项）
            List<QuizItem> quizItems = quizItemMapper.selectList(
                    new LambdaQueryWrapper<QuizItem>().eq(QuizItem::getEntryId, e.getId()).orderByAsc(QuizItem::getSortOrder));
            em.put("quizItems", quizItems.stream().map(q -> {
                Map<String, Object> qm = new LinkedHashMap<>();
                qm.put("id", q.getId());
                qm.put("definition", q.getDefinition());
                qm.put("difficulty", q.getDifficulty());
                qm.put("targetWord", q.getTargetWord());
                qm.put("kidRef", q.getKidRef());
                // 干扰项
                List<QuizDistractor> distractors = quizDistractorMapper.selectList(
                        new LambdaQueryWrapper<QuizDistractor>().eq(QuizDistractor::getQuizItemId, q.getId())
                                .orderByAsc(QuizDistractor::getSortOrder));
                qm.put("distractors", distractors.stream().map(QuizDistractor::getText).collect(Collectors.toList()));
                return qm;
            }).collect(Collectors.toList()));

            // 只读类词书才有用法
            if ("readonly".equals(book.getStudyMode())) {
                List<WordUsage> usages = wordUsageMapper.selectList(
                        new LambdaQueryWrapper<WordUsage>().eq(WordUsage::getEntryId, e.getId()).orderByAsc(WordUsage::getSortOrder));
                em.put("usages", usages.stream().map(u -> {
                    Map<String, Object> um = new LinkedHashMap<>();
                    um.put("usageType", u.getUsageType());
                    um.put("definition", u.getDefinition());
                    um.put("exampleSentence", u.getExampleSentence());
                    um.put("exampleTranslation", u.getExampleTranslation());
                    um.put("exampleSource", u.getExampleSource());
                    return um;
                }).collect(Collectors.toList()));
            }

            return em;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", book.getId());
        result.put("name", book.getName());
        result.put("description", book.getDescription());
        result.put("category", book.getCategory());
        result.put("coverColor", book.getCoverColor());
        result.put("studyMode", book.getStudyMode());
        result.put("identifyPrompt", book.getIdentifyPrompt());
        result.put("examLevel", book.getExamLevel());
        result.put("initialized", book.getInitialized());
        result.put("totalWords", book.getTotalWords());
        result.put("wordEntries", wordList);
        return result;
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
