package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("user_checkin")
public class UserCheckin {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private LocalDate checkinDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
