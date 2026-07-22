package com.bogutongjin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Web MVC 配置 — 注册登录拦截器和 @CurrentUser 参数解析器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final CurrentUserResolver currentUserResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/auth/**", "/api/admin/**");   // 登录和管理接口放行
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserResolver);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 头像上传文件映射：/upload/wyq/avatar/ → ~/upload/wyq/avatar/
        registry.addResourceHandler("/upload/wyq/avatar/**")
                .addResourceLocations("file:" + System.getProperty("user.home") + "/upload/wyq/avatar/");
        // TTS 音频文件映射：/upload/wyq/tts/ → ~/upload/wyq/tts/
        registry.addResourceHandler("/upload/wyq/tts/**")
                .addResourceLocations("file:" + System.getProperty("user.home") + "/upload/wyq/tts/");
    }
}
