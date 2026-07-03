package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
import com.bogutongjin.common.Result;
import com.bogutongjin.service.ProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;

    @GetMapping
    public Result<Map<String, Object>> getProgress(
            @RequestParam String wordBookId,
            @CurrentUser Long userId) {
        return Result.ok(progressService.getProgress(userId, wordBookId));
    }
}
