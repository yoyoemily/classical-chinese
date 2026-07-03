package com.bogutongjin.dto;

import lombok.Data;

/** 保存个人信息——字段选填，传哪个改哪个 */
@Data
public class SaveUserInfoRequest {
    private String avatarUrl;
    private String nickName;
    private String grade;
}
