package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 微信登录请求
 */
@Data
public class LoginRequest {
    /** wx.login() 返回的 code */
    @NotBlank(message = "code 不能为空")
    private String code;

    /** 小程序码 scene 值（格式: i_{userId}），扫码进入时携带 */
    private String scene;

    /** 分享卡片 inviter 参数，通过 onShareAppMessage 路径传入 */
    private Long inviterId;
}
