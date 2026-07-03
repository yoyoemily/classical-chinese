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

    @PostMapping("/import")
    public Result<Map<String, Object>> doImport(@RequestBody(required = false) Map<String, String> body) {
        String path = body != null ? body.getOrDefault("path", "data/source.json") : "data/source.json";
        long start = System.currentTimeMillis();
        importService.importFromJson(path);
        long elapsed = System.currentTimeMillis() - start;
        return Result.ok(Map.of("success", true, "elapsedMs", elapsed, "message", "数据源导入完成"));
    }
}
