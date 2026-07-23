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
    /** 所属小程序用户ID（公众号生成时可为空，用户在小程序输入后认领） */
    private Long userId;
    /** 公众号 OpenID（服务号扫码关注时记录，小程序管理员手动生成时为空） */
    private String mpOpenId;
    /** 0=未使用 1=已验证 2=已过期 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime verifiedAt;
}
