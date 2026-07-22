package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("classic_chapter")
public class ClassicChapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long classicId;
    private Long parentId;
    private String title;
    private String author;
    private String era;
    private String background;

    /** 章节音频 URL（讯飞 TTS 合成后写入） */
    private String audioUrl;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
