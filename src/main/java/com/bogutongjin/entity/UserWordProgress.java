package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_word_progress")
public class UserWordProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String wordBookId;
    private String wordId;
    private String stage;
    private LocalDate nextReviewDate;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer resetCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
