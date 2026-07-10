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

    @GetMapping
    public Result<List<Map<String, Object>>> listClassics(
            @RequestParam(required = false) String category) {
        return Result.ok(classicService.listClassics(category));
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getClassicDetail(@PathVariable Long id) {
        return Result.ok(classicService.getClassicDetail(id));
    }
}
