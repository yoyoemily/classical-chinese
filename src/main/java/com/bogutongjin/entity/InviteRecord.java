package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("invite_record")
public class InviteRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long inviterId;
    private Long inviteeId;
    private String sceneCode;
    private Integer sourceType;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime boundAt;
}
