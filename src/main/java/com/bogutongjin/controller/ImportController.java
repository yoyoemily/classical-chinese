package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.dto.GlossaryImportRequest;
import com.bogutongjin.dto.SourceData;
import com.bogutongjin.service.DataImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
        return Result.ok(Map.of("success", true, "elapsedMs", elapsed, "message", "勋章导入完成"));
    }

    @PostMapping("/clear-data")
    public Result<Map<String, Object>> clearData(@RequestParam(defaultValue = "all") String scope) {
        long start = System.currentTimeMillis();
        Map<String, Object> result;
        switch (scope) {
            case "user":
                result = importService.clearUserData();
                break;
            case "wordbook":
                result = importService.clearWordBookData();
                break;
            case "article":
                result = importService.clearArticleData();
                break;
            case "classic":
                result = importService.clearClassicData();
                break;
            case "all":
            default:
                result = importService.clearAll();
                break;
        }
        result.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.ok(result);
    }

    /**
     * 经典元数据全量导入（幂等 upsert）
     * 从知识库 classics.json 读取 52 部经典元数据
     */
    @PostMapping("/import/classics")
    public Result<Map<String, Object>> importClassics() {
        long start = System.currentTimeMillis();
        Map<String, Object> result = importService.importClassicsFromJson();
        result.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.ok(result);
    }

    /**
     * 选篇正文全量导入（幂等：先清空后插入）
     * 支持两种模式：
     *   - 无请求体：从服务器本地知识库 articles.json 读取（本地开发环境）
     *   - 有请求体：直接解析请求体 JSON（线上环境，客户端 -d @文件 传入）
     */
    @PostMapping("/import/articles")
    public Result<Map<String, Object>> importArticles(@RequestBody(required = false) String body) {
        long start = System.currentTimeMillis();
        Map<String, Object> result;
        if (body != null && !body.isBlank()) {
            result = importService.importArticlesFromJson(body);
        } else {
            result = importService.importArticlesFromJson();
        }
        result.put("elapsedMs", System.currentTimeMillis() - start);
        return Result.ok(result);
    }

    /**
     * 单篇典故注释导入（幂等：先删后插）
     */
    @PostMapping("/import/glossary/{articleId}")
    public Result<Map<String, Object>> importGlossary(@PathVariable String articleId,
                                                       @RequestBody GlossaryImportRequest request) {
        return Result.ok(importService.importGlossaryForArticle(articleId, request));
    }

    /**
     * 单本词书独立导入（幂等：先删后插）
     * 接收 SourceWordBook 格式的 JSON 请求体，只影响该本词书的数据
     */
    @PostMapping("/import/wordbook")
    public Result<Map<String, Object>> importWordBook(@RequestBody SourceData.SourceWordBook book) {
        return Result.ok(importService.importWordBook(book.getId(), book));
    }

    /**
     * 经典著作章节内容导入（幂等：先删后插）
     * 接收纯 chapters 数组 JSON，classicId 从路径参数传入
     */
    @PostMapping("/import/classic/{classicId}")
    public Result<Map<String, Object>> importClassicBook(
            @PathVariable Long classicId,
            @RequestBody List<SourceData.SourceClassicChapter> chapters) {
        importService.importClassicBook(classicId, chapters);
        return Result.ok(Map.of("success", true, "message", "经典 ID=" + classicId + " 导入完成"));
    }
}
