package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
import com.bogutongjin.common.Result;
import com.bogutongjin.service.BadgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    @GetMapping
    public Result<Map<String, Object>> getBadges(@CurrentUser Long userId) {
        return Result.ok(badgeService.getBadges(userId));
    }
}
