package com.bogutongjin.config;

import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 微信公众号服务号 Bean 配置
 */
@Configuration
@RequiredArgsConstructor
public class WechatMpConfig {

    private final WechatMpProperties mpProperties;

    @Bean
    public WxMpService wxMpService() {
        WxMpDefaultConfigImpl config = new WxMpDefaultConfigImpl();
        config.setAppId(mpProperties.getAppId());
        config.setSecret(mpProperties.getSecret());
        config.setToken(mpProperties.getToken());
        if (mpProperties.getAesKey() != null && !mpProperties.getAesKey().isBlank()) {
            config.setAesKey(mpProperties.getAesKey());
        }

        WxMpService service = new WxMpServiceImpl();
        service.setWxMpConfigStorage(config);
        return service;
    }
}
