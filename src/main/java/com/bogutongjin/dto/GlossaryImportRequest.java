package com.bogutongjin.dto;

import lombok.Data;
import java.util.List;

/**
 * 单篇典故注释导入请求
 * 格式与 data/glossary/art_XXX.json 一致
 */
@Data
public class GlossaryImportRequest {
    private String articleId;
    private String title;
    private List<SentenceGlossary> sentences;

    @Data
    public static class SentenceGlossary {
        /** 句子序号，从 0 开始，对应 article_sentence.sort_order */
        private Integer sentenceIndex;
        private List<GlossaryItem> glossary;
    }

    @Data
    public static class GlossaryItem {
        private String word;
        private String definition;
    }
}
