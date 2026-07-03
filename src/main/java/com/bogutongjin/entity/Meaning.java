package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("meaning")
public class Meaning {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String wordId;
    private String definition;
    private String pinyin;
    private String example;
    private String translation;
    private String source;
    private Integer sortOrder;
}
