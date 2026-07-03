package com.bogutongjin.service;

import cn.hutool.core.lang.TypeReference;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.bogutongjin.entity.User;
import com.bogutongjin.mapper.UserMapper;
import com.bogutongjin.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 认证服务 — 微信登录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;

    @Value("${wechat.app-id}")
    private String appId;

    @Value("${wechat.app-secret:}")
    private String appSecret;

    private static final String WX_CODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code";

    /**
     * 微信登录：code → openId → 查找/创建用户 → 签发 JWT
     *
     * @param code wx.login() 返回的临时 code
     * @return { token, userId }
     */
    public Map<String, Object> login(String code) {
        // 1. 获取 openId（正式环境走微信 API，开发环境 app-secret 缺失时用 code 做 openId）
        String openId = resolveOpenId(code);
        if (openId == null) {
            throw new RuntimeException("微信登录失败，请稍后重试");
        }

        // 2. 查找或创建用户
        User user = findOrCreateByOpenId(openId);

        // 3. 签发 JWT
        String token = jwtUtil.generate(user.getId());

        log.info("用户登录成功: userId={}, openId={}", user.getId(), openId);

        return Map.of("token", token, "userId", user.getId());
    }

    /**
     * 获取 openId：app-secret 已配置时走微信 code2session，否则用固定 dev openId（开发模式）。
     */
    private String resolveOpenId(String code) {
        if (appSecret != null && !appSecret.isBlank()) {
            return code2session(code);
        }
        // 开发模式：使用固定 openId，保证同一个开发者始终映射到同一个用户账号，
        // 不会每次 wx.login() 都创建新用户。
        // TODO: 正式上线前替换 WECHAT_APP_SECRET 环境变量为真实值
        log.warn("app-secret 未配置，开发模式：使用固定 dev openId");
        return "dev-openid";
    }

    /**
     * 调用微信 jscode2session 获取 openId
     */
    private String code2session(String code) {
        String url = String.format(WX_CODE2SESSION_URL, appId, appSecret, code);
        try {
            String body = HttpUtil.get(url);
            log.debug("微信 code2session 返回: {}", body);
            Map<String, Object> result = JSONUtil.toBean(body,
                    new TypeReference<Map<String, Object>>() {}, false);
            if (result != null && result.containsKey("openid")) {
                String openId = (String) result.get("openid");
                if (openId != null && !openId.isEmpty()) return openId;
            }
            // errcode 不为 0 时记录
            if (result != null && result.containsKey("errcode")) {
                log.error("微信 code2session 失败: errcode={}, errmsg={}",
                        result.get("errcode"), result.get("errmsg"));
            }
            return null;
        } catch (Exception e) {
            log.error("调微信 code2session 异常", e);
            return null;
        }
    }

    /**
     * 根据 openId 查找用户，不存在则自动创建
     */
    private User findOrCreateByOpenId(String openId) {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getOpenId, openId));
        if (user != null) return user;

        user = new User();
        user.setOpenId(openId);
        user.setTotalXp(0);
        user.setCurrentStreak(0);
        user.setLongestStreak(0);
        userMapper.insert(user);
        log.info("创建新用户: userId={}, openId={}", user.getId(), openId);
        return user;
    }
}
