package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("badge")
public class Badge {
    @TableId
    private String id;
    private String name;
    private String description;
    private String icon;
    private String category;
    private String conditionType;
    private Integer conditionValue;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
