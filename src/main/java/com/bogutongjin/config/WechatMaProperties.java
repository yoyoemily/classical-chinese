package com.bogutongjin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信小程序配置
 * 复用已有 YAML 中 wechat.app-id / wechat.app-secret 节点
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatMaProperties {

    /** 小程序 AppID */
    private String appId;

    /** 小程序 AppSecret */
    private String appSecret;
}
