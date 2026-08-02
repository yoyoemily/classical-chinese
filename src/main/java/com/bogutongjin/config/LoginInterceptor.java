package com.bogutongjin.config;

import com.bogutongjin.mapper.UserMapper;
import com.bogutongjin.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录拦截器 — 从 Authorization header 解析 JWT，注入 userId 到 request attribute
 *
 * <p>放行路径在 WebMvcConfig 中配置：/api/auth/login</p>
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    /** 节流缓存：userId → 上次更新 last_active_at 的时间戳(ms)，避免每次请求都写库 */
    private final ConcurrentHashMap<Long, Long> lastActiveUpdateCache = new ConcurrentHashMap<>();
    private static final long UPDATE_THROTTLE_MS = 60 * 60 * 1000; // 1 小时内不重复更新

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // OPTIONS 预检请求放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":10401,\"message\":\"未登录，请先授权\"}");
            } catch (Exception ignored) {}
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.parseUserId(token);
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":10401,\"message\":\"登录已过期，请重新登录\"}");
            } catch (Exception ignored) {}
            return false;
        }

        request.setAttribute("userId", userId);

        // 节流更新 last_active_at：1 小时内更新过则跳过，减少无效 DB 写入
        long now = System.currentTimeMillis();
        Long lastUpdate = lastActiveUpdateCache.get(userId);
        if (lastUpdate == null || now - lastUpdate > UPDATE_THROTTLE_MS) {
            lastActiveUpdateCache.put(userId, now);
            CompletableFuture.runAsync(() -> {
                try {
                    userMapper.updateLastActiveAt(userId);
                } catch (Exception ignored) {
                    // 更新失败不影响请求
                }
            });
        }

        return true;
    }
}

