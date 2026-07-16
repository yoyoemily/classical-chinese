package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("word_book_entry")
public class WordBookEntry implements Serializable {
    @TableId
    private String id;
    private String wordBookId;
    @TableField("`character`")
    private String character;
    private String pinyin;
    private String characterType;
    private String explanation;
    private String oracleForm;
    private String examFrequency;
    private String mnemonic;
    private String wordType;
    private String similarHomophones;
    private String similarShapes;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
