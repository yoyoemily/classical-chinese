package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("classic_paragraph")
public class ClassicParagraph {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long chapterId;
    private Integer sortOrder;
    private String text;
    private String translation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
