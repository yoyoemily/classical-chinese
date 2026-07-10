package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("sentence")
public class Sentence {
    @TableId
    private String id;
    private String wordId;
    private String text;
    private String source;
    private String translation;
    private String targetWord;
    private Integer correctMeaningIndex;
    private String difficulty;
    private String articleId;
    private String audioUrl;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
