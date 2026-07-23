package com.bogutongjin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("redeem_code")
public class RedeemCode {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private Long userId;
    /** 0=未使用 1=已验证 2=已过期 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;
}
