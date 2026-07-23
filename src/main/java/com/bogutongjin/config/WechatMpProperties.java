package com.bogutongjin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号服务号配置
 * 对应 application.yml 中 wechat.mp 节点
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat.mp")
public class WechatMpProperties {

    /** 服务号 AppID */
    private String appId;

    /** 服务号 AppSecret */
    private String secret;

    /** 消息校验 Token（MP 后台配置的一致） */
    private String token;

    /** 消息加解密 Key（安全模式，可选） */
    private String aesKey;
}
