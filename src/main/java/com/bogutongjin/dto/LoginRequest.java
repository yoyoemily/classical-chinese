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
}
