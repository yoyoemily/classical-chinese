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
}
