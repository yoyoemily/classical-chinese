package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 提交意见建议 */
@Data
public class SubmitSuggestionRequest {
    @NotBlank(message = "content 不能为空")
    private String content;

    private String contact;
    private String category;
}
