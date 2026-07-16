package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;

@Data
@TableName("quiz_item")
public class QuizItem implements Serializable {
    @TableId
    private String id;
    private String entryId;
    private String kidRef;
    private String difficulty;
    private String targetWord;
    private String definition;
    private String sentenceText;
    private String sentenceTranslation;
    private String sentenceSource;
    private Integer sortOrder;
}
