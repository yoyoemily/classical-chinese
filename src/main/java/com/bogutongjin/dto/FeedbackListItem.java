package com.bogutongjin.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 反馈列表项（精简字段，不含 context 详情） */
@Data
public class FeedbackListItem {
    private Long id;
    private String category;
    private String source;
    private String description;
    private String nodeTitle;
    private String articleTitle;
    private String className;
    private Integer resolved;
    private String reply;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
