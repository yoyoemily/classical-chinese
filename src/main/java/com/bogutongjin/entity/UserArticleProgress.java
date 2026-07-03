package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_article_progress")
public class UserArticleProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String articleId;
    private Integer readProgress;
    private String mastery;
    private LocalDate lastReadDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
