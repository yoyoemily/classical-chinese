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

    private final WordMapper wordMapper;
    private final MeaningMapper meaningMapper;
    private final SentenceMapper sentenceMapper;
    private final SentenceDistractorMapper sentenceDistractorMapper;
    private final SimilarHomophoneMapper similarHomophoneMapper;
    private final SimilarShapeMapper similarShapeMapper;
    private final WordBookMapper wordBookMapper;

    public Map<String, Object> getWordDetail(String wordId) {
        Word word = wordMapper.selectById(wordId);
        if (word == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", word.getId());
        result.put("character", word.getCharacter());
        result.put("pinyin", word.getPinyin());
        result.put("characterType", word.getCharacterType());
        result.put("explanation", word.getExplanation());
        result.put("examFrequency", word.getExamFrequency());
        result.put("mnemonic", word.getMnemonic());
        result.put("wordType", word.getWordType());

        // 义项
        List<Meaning> meanings = meaningMapper.selectList(
                new LambdaQueryWrapper<Meaning>().eq(Meaning::getWordId, wordId).orderByAsc(Meaning::getSortOrder));
        result.put("meanings", meanings.stream().map(m -> {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("definition", m.getDefinition());
            mm.put("pinyin", m.getPinyin());
            mm.put("example", m.getExample());
            mm.put("translation", m.getTranslation());
            mm.put("source", m.getSource());
            return mm;
        }).collect(Collectors.toList()));

        // 句子
        List<Sentence> sentences = sentenceMapper.selectList(
                new LambdaQueryWrapper<Sentence>().eq(Sentence::getWordId, wordId).orderByAsc(Sentence::getSortOrder));
        result.put("sentences", sentences.stream().map(s -> {
            Map<String, Object> sm = new LinkedHashMap<>();
            sm.put("id", s.getId());
            sm.put("text", s.getText());
            sm.put("source", s.getSource());
            sm.put("translation", s.getTranslation());
            sm.put("targetWord", s.getTargetWord());
            sm.put("correctMeaningIndex", s.getCorrectMeaningIndex());
            sm.put("difficulty", s.getDifficulty());
            sm.put("articleId", s.getArticleId());
            sm.put("audioUrl", s.getAudioUrl());
            List<SentenceDistractor> distractors = sentenceDistractorMapper.selectList(
                    new LambdaQueryWrapper<SentenceDistractor>().eq(SentenceDistractor::getSentenceId, s.getId())
                            .orderByAsc(SentenceDistractor::getSortOrder));
            sm.put("distractors", distractors.stream().map(SentenceDistractor::getText).collect(Collectors.toList()));
            return sm;
        }).collect(Collectors.toList()));

        // 同音易混
        List<SimilarHomophone> homophones = similarHomophoneMapper.selectList(
                new LambdaQueryWrapper<SimilarHomophone>().eq(SimilarHomophone::getWordId, wordId).orderByAsc(SimilarHomophone::getSortOrder));
        result.put("similarHomophones", homophones.stream().map(SimilarHomophone::getCharacter).collect(Collectors.toList()));

        // 形近字
        List<SimilarShape> shapes = similarShapeMapper.selectList(
                new LambdaQueryWrapper<SimilarShape>().eq(SimilarShape::getWordId, wordId).orderByAsc(SimilarShape::getSortOrder));
        result.put("similarShapes", shapes.stream().map(SimilarShape::getCharacter).collect(Collectors.toList()));

        return result;
    }

    /** 全局搜索字词 */
    public List<Map<String, Object>> searchWords(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return List.of();

        List<Word> words = wordMapper.selectList(
                new LambdaQueryWrapper<Word>().like(Word::getCharacter, keyword));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Word word : words) {
            // 跳过只读类词书（虚词深度解析等）
            WordBook book = wordBookMapper.selectById(word.getWordBookId());
            if (book == null || "readonly".equals(book.getStudyMode())) continue;

            // 获取所有义项
            List<Meaning> meanings = meaningMapper.selectList(
                    new LambdaQueryWrapper<Meaning>().eq(Meaning::getWordId, word.getId())
                            .orderByAsc(Meaning::getSortOrder));

            List<Map<String, Object>> meaningList = new ArrayList<>();
            for (Meaning m : meanings) {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("definition", m.getDefinition());
                mm.put("example", m.getExample());
                mm.put("translation", m.getTranslation());
                mm.put("source", m.getSource());
                meaningList.add(mm);
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wordId", word.getId());
            item.put("character", word.getCharacter());
            item.put("pinyin", word.getPinyin());
            item.put("meanings", meaningList);
            item.put("wordBookName", book.getName());
            item.put("wordBookId", word.getWordBookId());
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

        // 2. 批量查出所有 word
        List<Word> words = wordMapper.selectList(
                new LambdaQueryWrapper<Word>().in(Word::getWordBookId, bookIds)
                        .orderByAsc(Word::getSortOrder));

        // 3. 按 wordType 分组，shi/xu 合并为 shixu
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        result.put("shixu", new ArrayList<>());
        result.put("tongjia", new ArrayList<>());
        result.put("huoyong", new ArrayList<>());
        result.put("gujinyi", new ArrayList<>());

        for (Word word : words) {
            String key = resolveGroupKey(word.getWordType());
            if (key == null) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wordId", word.getId());
            item.put("character", word.getCharacter());
            item.put("pinyin", word.getPinyin());
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
}
