package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("user_answer_history")
public class UserAnswerHistory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String wordBookId;
    private String entryId;
    private String quizItemId;
    private Integer selectedOption;
    private Integer correct;
    private Long timestampMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
