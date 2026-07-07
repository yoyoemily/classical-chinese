package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("study_mistake")
public class StudyMistake {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String wordId;
    private String wordBookId;
    private String sentenceText;
    private String wrongAnswer;
    private String correctAnswer;
    private Integer mistakeCount;
    private LocalDateTime lastMistakeTime;
    private Integer consecutiveCorrect;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
