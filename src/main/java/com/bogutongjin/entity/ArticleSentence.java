package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("article_sentence")
public class ArticleSentence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String articleId;
    private String text;
    private String translation;
    private String audioUrl;
    private Integer sortOrder;
}
