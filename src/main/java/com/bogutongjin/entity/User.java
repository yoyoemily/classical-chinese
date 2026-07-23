package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String openId;
    private String unionId;
    private String avatarUrl;
    private String nickName;
    private String grade;
    private Integer totalXp;
    private Integer currentStreak;
    private Integer longestStreak;
    private Integer memberLevel;

    private Integer deleted;

    private LocalDateTime dataClearedAt;

    private LocalDateTime lastActiveAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
