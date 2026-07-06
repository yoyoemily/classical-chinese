package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台 — 数据导入接口
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ImportController {

    private final DataImportService importService;

    /**
     * 简单的 POST 测试接口，验证后端连通性和请求链路
     */
    @PostMapping("/ping")
    public Result<Map<String, Object>> ping() {
        return Result.ok(Map.of(
                "status", "ok",
                "timestamp", System.currentTimeMillis(),
                "message", "POST 接口联通正常"
        ));
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> doImport() {
        long start = System.currentTimeMillis();
        importService.importFromJson();
        long elapsed = System.currentTimeMillis() - start;
        return Result.ok(Map.of("success", true, "elapsedMs", elapsed, "message", "数据源导入完成"));
    }
}
