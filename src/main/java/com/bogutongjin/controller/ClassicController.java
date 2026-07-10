package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.ClassicService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classics")
@RequiredArgsConstructor
public class ClassicController {

    private final ClassicService classicService;

    /** 经典著作列表 */
    @GetMapping
    public Result<List<Map<String, Object>>> listClassics(
            @RequestParam(required = false) String category) {
        return Result.ok(classicService.listClassics(category));
    }

    /**
     * 经典基本信息 + 目录树（轻量）
     * loadMode=full 时顺带返回全文 chapters 字段
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> getClassicMeta(@PathVariable Long id) {
        return Result.ok(classicService.getClassicMeta(id));
    }

    /** 按需加载内容块 */
    @GetMapping("/{id}/content/{nodeId}")
    public Result<Map<String, Object>> getClassicContent(
            @PathVariable Long id,
            @PathVariable String nodeId) {
        return Result.ok(classicService.getClassicContent(id, nodeId));
    }
}
