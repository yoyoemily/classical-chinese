package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("article_glossary")
public class ArticleGlossary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleSentenceId;
    private String word;
    private String definition;
    private Integer sortOrder;
}
