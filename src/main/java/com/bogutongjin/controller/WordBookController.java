package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.WordBookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wordbooks")
@RequiredArgsConstructor
public class WordBookController {

    private final WordBookService wordBookService;

    @GetMapping
    public Result<List<Map<String, Object>>> getWordBooks() {
        return Result.ok(wordBookService.getWordBooks());
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getWordBookDetail(@PathVariable String id) {
        Map<String, Object> detail = wordBookService.getWordBookDetail(id);
        if (detail == null) return Result.fail(10003, "词书不存在");
        return Result.ok(detail);
    }
}
