package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("word_entry_keyword_ref")
public class WordEntryKeywordRef implements Serializable {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String entryId;
    private String kid;
    private Integer sortOrder;
}
