package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.VocabularyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/vocabulary")
@RequiredArgsConstructor
public class VocabularyController {

    private final VocabularyService vocabularyService;

    @GetMapping
    public Result<Map<String, Object>> getVocabulary(
            @RequestParam String wordBookId,
            @RequestParam String tab,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(vocabularyService.getVocabulary(userId, wordBookId, tab));
    }
}
