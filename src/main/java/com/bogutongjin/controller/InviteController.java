package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.InviteService;
import com.bogutongjin.util.JwtUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Map;

/**
 * 邀请接口
 * GET  /api/invite/poster?token=xxx  — 获取用户专属海报（返回 PNG）
 * GET  /api/invite/stats            — 获取邀请统计
 */
@Slf4j
@RestController
@RequestMapping("/api/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;
    private final JwtUtil jwtUtil;

    /**
     * 获取用户专属海报。
     * 该端点不走 LoginInterceptor（已排除），从 query param 取 token 手动解析 JWT。
     * 原因是 wx.downloadFile 无法携带 Authorization header。
     */
    @GetMapping(value = "/poster", produces = MediaType.IMAGE_PNG_VALUE)
    public void getPoster(@RequestParam(value = "token", required = false) String token,
                          HttpServletResponse response) throws IOException {
        // 手动解析 JWT
        Long userId = null;
        if (token != null && !token.isBlank()) {
            userId = jwtUtil.parseUserId(token);
        }
        if (userId == null) {
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":10401,\"message\":\"未登录\"}");
            return;
        }

        byte[] posterBytes = inviteService.generatePoster(userId);

        response.setHeader("Cache-Control", "max-age=3600");
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.getOutputStream().write(posterBytes);
    }

    /**
     * 获取当前用户的邀请统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(@RequestAttribute("userId") Long userId) {
        long count = inviteService.getInviteCount(userId);
        return Result.ok(Map.of("totalInvited", count));
    }
}
