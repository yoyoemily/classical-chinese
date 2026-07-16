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
    private String kid;
    /** 消歧用：多字上下文片段，用于定位句中具体出现位置 */
    private String matchWord;
    /** 生词类型：shi/xu/tongjia/gujinyi/huoyong */
    private String wordType;
    private Integer sortOrder;
}
