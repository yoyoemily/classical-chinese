package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("word_usage")
public class WordUsage implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryId;
    private String usageType;
    private String definition;
    private String exampleSentence;
    private String exampleTranslation;
    private String exampleSource;
    private Integer sortOrder;
}
