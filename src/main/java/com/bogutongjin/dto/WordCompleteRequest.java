package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 完成单个字词的学习（所有句子答完后调用） */
@Data
public class WordCompleteRequest {
    @NotBlank(message = "wordBookId 不能为空")
    private String wordBookId;
    @NotBlank(message = "entryId 不能为空")
    private String entryId;
}
