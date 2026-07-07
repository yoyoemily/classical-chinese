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
    @NotBlank(message = "wordId 不能为空")
    private String wordId;
    @NotBlank(message = "sentenceId 不能为空")
    private String sentenceId;
    @NotNull(message = "selectedOption 不能为空")
    private Integer selectedOption;
    @NotNull(message = "correct 不能为空")
    private Boolean correct;
    /** 正确答案文本（由前端直接传入，避免后端重建选项列表时因 shuffle 导致序号不匹配） */
    private String correctAnswer;
    /** 用户选择的答案文本（由前端直接传入） */
    private String wrongAnswer;
}
