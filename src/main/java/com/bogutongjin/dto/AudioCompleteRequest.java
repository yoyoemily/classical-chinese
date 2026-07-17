package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 音频听读完成请求（后端根据 contentId 自己查原文统计汉字，防止前端作弊） */
@Data
public class AudioCompleteRequest {
    @NotBlank(message = "contentType 不能为空")
    private String contentType;   // "article" | "classic_chapter"

    @NotBlank(message = "contentId 不能为空")
    private String contentId;     // articleId 或 classicId:nodeId
}
