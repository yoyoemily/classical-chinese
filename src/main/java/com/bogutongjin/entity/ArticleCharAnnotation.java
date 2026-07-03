package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("article_char_annotation")
public class ArticleCharAnnotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleSentenceId;
    private String charText;
    @TableField("`role`")
    private String role;
    private String definition;
    private Integer sortOrder;
}
