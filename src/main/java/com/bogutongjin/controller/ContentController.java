package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    @GetMapping("/words/{id}")
    public Result<Map<String, Object>> getWordDetail(@PathVariable String id) {
        Map<String, Object> detail = contentService.getWordDetail(id);
        if (detail == null) return Result.fail(10003, "字词不存在");
        return Result.ok(detail);
    }

    @GetMapping("/words/search")
    public Result<List<Map<String, Object>>> searchWords(@RequestParam String keyword) {
        return Result.ok(contentService.searchWords(keyword));
    }
}
