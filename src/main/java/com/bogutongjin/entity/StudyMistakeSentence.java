package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("study_mistake_sentence")
public class StudyMistakeSentence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long mistakeId;
    private String sentenceId;
    private String sentenceText;
    private String wrongAnswer;
    private String correctAnswer;
    private Integer mistakeCount;
    private Integer consecutiveCorrect;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
