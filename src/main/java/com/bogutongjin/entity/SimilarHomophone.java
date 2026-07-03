package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("similar_homophone")
public class SimilarHomophone {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String wordId;
    @TableField("`character`")
    private String character;
    private Integer sortOrder;
}
