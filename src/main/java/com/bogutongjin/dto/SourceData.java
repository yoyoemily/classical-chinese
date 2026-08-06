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
        private List<SourceWordEntry> wordEntries;
    }

    // ---- 字词条目 ----
    @Data
    public static class SourceWordEntry {
        private String id;
        private String character;
        private String pinyin;
        private String characterType;
        private String explanation;
        private String oracleForm;
        private String examFrequency;
        private String mnemonic;
        private String wordType;
        private List<String> similarHomophones;
        private List<String> similarShapes;
        private List<SourceQuizItem> quizItems;
        private List<SourceWordUsage> usages;
    }

    // ---- 考题 ----
    @Data
    public static class SourceQuizItem {
        private String id;
        private String kidRef;
        private String targetWord;
        private String difficulty;
        private String definition;
        private List<String> distractors;
        private String sentenceText;
        private String sentenceTranslation;
        private String sentenceSource;
    }

    // ---- 字词用法 ----
    @Data
    public static class SourceWordUsage {
        private String usageType;
        private String definition;
        private String exampleSentence;
        private String exampleTranslation;
        private String exampleSource;
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
        /** 是否可阅读：1=正常文章，0=壳文章（不可见）。导入时必填，不设则报错。 */
        private Integer hasContent;
        private List<SourceArticleSentence> sentences;
    }

    @Data
    public static class SourceArticleSentence {
        private String text;
        private String translation;
        private List<SourceKeyWord> keyWords;
        private String audioUrl;
        private List<SourceGlossaryItem> glossary;
    }

    @Data
    public static class SourceKeyWord {
        private String word;
        private String definition;
        private String masteryLevel;
        private String kid;
        /** 消歧用：多字上下文片段，用于定位句中具体出现位置 */
        private String matchWord;
        /** 生词类型：shi/xu/tongjia/gujinyi/huoyong */
        private String wordType;
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
        private Integer sortOrder;
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
        private String background;
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

    // ---- 经典典故注释独立导入 ----

    /** 经典典故注释导入：顶层数组元素。有 entries → 选集型，否则 → 章节型 */
    @Data
    public static class ClassicGlossaryImportChapter {
        @JsonProperty("chapterTitle")
        private String chapterTitle;
        /** 选集型：该门类下的条目列表 */
        private List<ClassicGlossaryImportEntry> entries;
        /** 章节型：该章下的段落列表 */
        private List<ClassicGlossaryImportParagraph> paragraphs;
    }

    /** 选集型条目 */
    @Data
    public static class ClassicGlossaryImportEntry {
        @JsonProperty("entryTitle")
        private String entryTitle;
        private List<ClassicGlossaryImportParagraph> paragraphs;
    }

    /** 段落匹配键 + glossary 数组 */
    @Data
    public static class ClassicGlossaryImportParagraph {
        private Integer sortOrder;
        private List<SourceClassicGlossary> glossary;
    }
}
