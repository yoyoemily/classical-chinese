package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("quiz_distractor")
public class QuizDistractor implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String quizItemId;
    private String text;
    private Integer sortOrder;
}
