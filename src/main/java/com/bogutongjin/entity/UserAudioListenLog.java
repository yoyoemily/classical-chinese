package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_audio_listen_log")
public class UserAudioListenLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String contentType;
    private String contentId;
    private Integer xpAwarded;
    private Integer textLength;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
