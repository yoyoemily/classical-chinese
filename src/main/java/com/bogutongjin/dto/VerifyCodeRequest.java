package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyCodeRequest {
    @NotBlank(message = "学习码不能为空")
    private String code;
}
