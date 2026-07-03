package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_badge")
public class UserBadge {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String badgeId;
    private LocalDate earnedDate;
    private Integer notified;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
