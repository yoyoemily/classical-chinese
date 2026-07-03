package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("sentence_distractor")
public class SentenceDistractor {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sentenceId;
    private String text;
    private Integer sortOrder;
}
