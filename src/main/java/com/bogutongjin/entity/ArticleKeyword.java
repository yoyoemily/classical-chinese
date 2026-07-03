package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

@Data
@TableName("article_keyword")
public class ArticleKeyword {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleSentenceId;
    private String wordText;
    private String definition;
    private String wordBookId;
    private String masteryLevel;
    private Integer sortOrder;
}
