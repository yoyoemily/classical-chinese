package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("feedback")
public class Feedback {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
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

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
