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
    private final WordMapper wordMapper;
    private final MeaningMapper meaningMapper;
    private final SentenceMapper sentenceMapper;
    private final SentenceDistractorMapper sentenceDistractorMapper;
    private final SimilarHomophoneMapper similarHomophoneMapper;
    private final SimilarShapeMapper similarShapeMapper;

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
                    map.put("totalWords", b.getTotalWords());
                    return map;
                }).collect(Collectors.toList());
    }

    /** 获取词书详情（含所有字词、义项、句子、干扰项） */
    public Map<String, Object> getWordBookDetail(String bookId) {
        WordBook book = wordBookMapper.selectById(bookId);
        if (book == null) return null;

        List<Word> words = wordMapper.selectList(
                new LambdaQueryWrapper<Word>().eq(Word::getWordBookId, bookId).orderByAsc(Word::getSortOrder));

        List<Map<String, Object>> wordList = words.stream().map(w -> {
            Map<String, Object> wm = new LinkedHashMap<>();
            wm.put("id", w.getId());
            wm.put("character", w.getCharacter());
            wm.put("pinyin", w.getPinyin());
            wm.put("characterType", w.getCharacterType());
            wm.put("explanation", w.getExplanation());
            wm.put("oracleForm", w.getOracleForm());
            wm.put("examFrequency", w.getExamFrequency());
            wm.put("mnemonic", w.getMnemonic());
            wm.put("wordType", w.getWordType());

            // 义项
            List<Meaning> meanings = meaningMapper.selectList(
                    new LambdaQueryWrapper<Meaning>().eq(Meaning::getWordId, w.getId()).orderByAsc(Meaning::getSortOrder));
            wm.put("meanings", meanings.stream().map(m -> {
                Map<String, Object> mm = new LinkedHashMap<>();
                mm.put("definition", m.getDefinition());
                mm.put("pinyin", m.getPinyin());
                mm.put("example", m.getExample());
                mm.put("translation", m.getTranslation());
                mm.put("source", m.getSource());
                return mm;
            }).collect(Collectors.toList()));

            // 句子（含干扰项）
            List<Sentence> sentences = sentenceMapper.selectList(
                    new LambdaQueryWrapper<Sentence>().eq(Sentence::getWordId, w.getId()).orderByAsc(Sentence::getSortOrder));
            wm.put("sentences", sentences.stream().map(s -> {
                Map<String, Object> sm = new LinkedHashMap<>();
                sm.put("id", s.getId());
                sm.put("text", s.getText());
                sm.put("source", s.getSource());
                sm.put("translation", s.getTranslation());
                sm.put("targetWord", s.getTargetWord());
                sm.put("correctMeaningIndex", s.getCorrectMeaningIndex());
                sm.put("difficulty", s.getDifficulty());
                sm.put("fullText", s.getFullText());
                sm.put("articleId", s.getArticleId());
                sm.put("audioUrl", s.getAudioUrl());
                // 干扰项
                List<SentenceDistractor> distractors = sentenceDistractorMapper.selectList(
                        new LambdaQueryWrapper<SentenceDistractor>().eq(SentenceDistractor::getSentenceId, s.getId())
                                .orderByAsc(SentenceDistractor::getSortOrder));
                sm.put("distractors", distractors.stream().map(SentenceDistractor::getText).collect(Collectors.toList()));
                return sm;
            }).collect(Collectors.toList()));

            // 同音易混字
            List<SimilarHomophone> homophones = similarHomophoneMapper.selectList(
                    new LambdaQueryWrapper<SimilarHomophone>().eq(SimilarHomophone::getWordId, w.getId()).orderByAsc(SimilarHomophone::getSortOrder));
            wm.put("similarHomophones", homophones.stream().map(SimilarHomophone::getCharacter).collect(Collectors.toList()));

            // 形近字
            List<SimilarShape> shapes = similarShapeMapper.selectList(
                    new LambdaQueryWrapper<SimilarShape>().eq(SimilarShape::getWordId, w.getId()).orderByAsc(SimilarShape::getSortOrder));
            wm.put("similarShapes", shapes.stream().map(SimilarShape::getCharacter).collect(Collectors.toList()));

            return wm;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", book.getId());
        result.put("name", book.getName());
        result.put("description", book.getDescription());
        result.put("category", book.getCategory());
        result.put("coverColor", book.getCoverColor());
        result.put("studyMode", book.getStudyMode());
        result.put("identifyPrompt", book.getIdentifyPrompt());
        result.put("totalWords", book.getTotalWords());
        result.put("words", wordList);
        return result;
    }
}
