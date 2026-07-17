package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 完成今日学习 */
@Data
public class CompleteStudyRequest {
    @NotBlank(message = "wordBookId 不能为空")
    private String wordBookId;
    @NotNull(message = "correctCount 不能为空")
    private Integer correctCount;
    @NotNull(message = "wrongCount 不能为空")
    private Integer wrongCount;
    /** 本次获得经验值（仅新学词答对才计入） */
    private Integer xpGained;
}
