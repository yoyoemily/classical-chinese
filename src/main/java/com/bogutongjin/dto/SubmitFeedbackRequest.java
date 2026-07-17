package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提交错误反馈 */
@Data
public class SubmitFeedbackRequest {
    @NotBlank(message = "category 不能为空")
    private String category;
    @NotBlank(message = "source 不能为空")
    private String source;
    @NotBlank(message = "description 不能为空")
    private String description;

    private Context context;

    @Data
    public static class Context {
        private String sentenceId;
        private String wordId;
        private String articleId;
        private String readingMode;
        private Integer classicId;
        private String nodeId;
        private String nodeTitle;
        private String sentenceText;
        private String articleTitle;
        private String className;
    }
}
