package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("classic")
public class Classic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String era;
    private String author;
    private String icon;
    private String description;
    private String category;
    private String structureType;
    private String loadMode;
    private String navMode;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
