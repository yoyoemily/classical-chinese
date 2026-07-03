package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping
    public Result<List<Map<String, Object>>> getArticles(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String textbook) {
        return Result.ok(articleService.getArticles(category, textbook));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getArticleDetail(@PathVariable String id) {
        return Result.ok(articleService.getArticleDetail(id));
    }
}
