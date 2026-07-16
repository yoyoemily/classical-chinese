package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/** 提交答题结果 */
@Data
public class SubmitAnswerRequest {
    @NotBlank(message = "wordBookId 不能为空")
    private String wordBookId;
    @NotBlank(message = "entryId 不能为空")
    private String entryId;
    @NotBlank(message = "quizItemId 不能为空")
    private String quizItemId;
    @NotNull(message = "selectedOption 不能为空")
    private Integer selectedOption;
    @NotNull(message = "correct 不能为空")
    private Boolean correct;
    /** 正确答案文本（由前端直接传入，避免后端重建选项列表时因 shuffle 导致序号不匹配） */
    private String correctAnswer;
    /** 用户选择的答案文本（由前端直接传入） */
    private String wrongAnswer;
}
