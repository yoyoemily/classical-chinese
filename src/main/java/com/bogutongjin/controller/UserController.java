package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
import com.bogutongjin.common.Result;
import com.bogutongjin.dto.SaveUserInfoRequest;
import com.bogutongjin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 获取等级信息 */
    @GetMapping("/profile")
    public Result<Map<String, Object>> getProfile(@CurrentUser Long userId) {
        Map<String, Object> profile = userService.getUserProfile(userId);
        if (profile == null) return Result.fail(10003, "用户不存在");
        return Result.ok(profile);
    }

    /** 获取个人信息 */
    @GetMapping("/info")
    public Result<Map<String, Object>> getInfo(@CurrentUser Long userId) {
        Map<String, Object> info = userService.getUserInfo(userId);
        if (info == null) return Result.fail(10003, "用户不存在");
        return Result.ok(info);
    }

    /** 保存个人信息——字段选填，传哪个改哪个 */
    @PutMapping("/info")
    public Result<Void> saveInfo(
            @RequestBody SaveUserInfoRequest req,
            @CurrentUser Long userId) {
        userService.saveUserInfo(userId, req.getAvatarUrl(), req.getNickName(), req.getGrade());
        return Result.ok();
    }

    /** 记录用户分享 */
    @PostMapping("/share")
    public Result<Map<String, Object>> recordShare(@CurrentUser Long userId) {
        userService.recordShare(userId);
        return Result.ok(Map.of("hasShared", true));
    }
}
