package com.bogutongjin.config;

import com.bogutongjin.mapper.UserMapper;
import com.bogutongjin.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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

        // 异步更新 last_active_at（不阻塞请求）
        try {
            userMapper.updateLastActiveAt(userId);
        } catch (Exception ignored) {
            // 更新失败不影响请求
        }

        return true;
    }
}

