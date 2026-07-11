package com.bogutongjin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 冷启动数据源 JSON 结构
 * 与 data/source.json 一一对应，用于 Jackson 反序列化
 */
@Data
public class SourceData {

    private String version;
    private String description;

    @JsonProperty("generatedAt")
    private String generatedAt;

    @JsonProperty("wordBooks")
    private List<SourceWordBook> wordBooks;

    private List<SourceArticle> articles;
    private List<SourceBadge> badges;
    private List<SourceClassic> classics;

    // ---- 词书 ----
    @Data
    public static class SourceWordBook {
        private String id;
        private String name;
        private String description;
        private String category;
        private String coverColor;
        private String studyMode;
        private String identifyPrompt;
        private String examLevel;
        private Boolean initialized;
        private Integer totalWords;
        private Integer sortOrder;
        private List<SourceWord> words;
    }

    // ---- 字词 ----
    @Data
    public static class SourceWord {
        private String id;
        private String character;
        private String pinyin;
        private String characterType;
        private String explanation;
        private String examFrequency;
        private String oracleForm;
        private List<SourceMeaning> meanings;
        private List<SourceSentence> sentences;
        private List<String> similarHomophones;
        private List<String> similarShapes;
        private String mnemonic;
        private String wordType;
    }

    // ---- 义项 ----
    @Data
    public static class SourceMeaning {
        private String definition;
        private String pinyin;
        private String example;
        private String translation;
        private String source;
    }

    // ---- 考题句子 ----
    @Data
    public static class SourceSentence {
        private String id;
        private String text;
        private String source;
        private String translation;
        private String targetWord;
        private Integer correctMeaningIndex;
        private String difficulty;
        private List<String> distractors;
        private String articleId;
        private String audioUrl;
    }

    // ---- 名篇 ----
    @Data
    public static class SourceArticle {
        private String id;
        private String title;
        private String author;
        private String dynasty;
        private String category;
        private String textbook;
        private String background;
        private String fullTextAudioUrl;
        private List<SourceArticleSentence> sentences;
        private List<String> relatedWordIds;
    }

    @Data
    public static class SourceArticleSentence {
        private String text;
        private String translation;
        private List<SourceKeyWord> keyWords;
        private String audioUrl;
        private List<SourceCharAnnotation> charAnnotations;
        private List<SourceGlossaryItem> glossary;
    }

    @Data
    public static class SourceKeyWord {
        private String word;
        private String definition;
        private String wordBookId;
        private String masteryLevel;
    }

    @Data
    public static class SourceCharAnnotation {
        private String charText;
        private String role;
        private String definition;
    }

    @Data
    public static class SourceGlossaryItem {
        private String word;
        private String definition;
    }

    // ---- 勋章 ----
    @Data
    public static class SourceBadge {
        private String id;
        private String name;
        private String description;
        private String icon;
        private String category;
        private Condition condition;

        @Data
        public static class Condition {
            private String type;
            private Integer value;
        }
    }

    // ---- 经典著作 ----
    @Data
    public static class SourceClassic {
        private Integer id;
        private String name;
        private String era;
        private String author;
        private String icon;
        private String description;
        private String category;
        private String structureType;
        private String loadMode;
        private String navMode;
    }

    // ---- 经典章节型数据（独立导入用） ----
    @Data
    public static class SourceClassicBook {
        private Integer id;
        private String name;
        private String author;
        private String era;
        private String category;
        private String description;
        private List<SourceClassicChapter> chapters;
    }

    @Data
    public static class SourceClassicChapter {
        private Integer id;
        private String title;
        private List<SourceClassicParagraph> paragraphs;
        /** 选集型：该门类下的条目列表 */
        private List<SourceAnthologyEntry> entries;
    }

    @Data
    public static class SourceAnthologyEntry {
        private String title;
        private String author;
        private String era;
        private List<SourceClassicParagraph> paragraphs;
    }

    @Data
    public static class SourceClassicParagraph {
        private String text;
        private String translation;
        private List<SourceClassicGlossary> glossary;
    }

    @Data
    public static class SourceClassicGlossary {
        private String word;
        private String explanation;
    }
}
