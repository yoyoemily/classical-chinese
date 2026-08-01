package com.bogutongjin.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 反馈详情（含完整 context 字段 + reply + readAt） */
@Data
public class FeedbackDetailResponse {
    private Long id;
    private String category;
    private String source;
    private String description;
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
    private Integer resolved;
    private String reply;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
