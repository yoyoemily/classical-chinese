package com.bogutongjin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 保存个人信息 */
@Data
public class SaveUserInfoRequest {
    @NotBlank(message = "avatarUrl 不能为空")
    private String avatarUrl;
    @NotBlank(message = "nickName 不能为空")
    private String nickName;
    @NotBlank(message = "grade 不能为空")
    private String grade;
}
