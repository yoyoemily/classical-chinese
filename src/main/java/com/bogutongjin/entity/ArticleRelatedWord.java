package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("article_related_word")
public class ArticleRelatedWord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String articleId;
    private String wordId;
}
