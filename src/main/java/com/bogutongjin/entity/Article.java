package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId
    private String id;
    private String title;
    private String author;
    private String dynasty;
    private String category;
    private String textbook;
    private String background;
    private String fullTextAudioUrl;
    private Integer sortOrder;
    private Integer hasContent;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
