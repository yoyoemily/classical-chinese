package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
import com.bogutongjin.common.Result;
import com.bogutongjin.dto.SubmitSuggestionRequest;
import com.bogutongjin.service.SuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/suggestion")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;

    @PostMapping
    public Result<Map<String, Object>> submitSuggestion(
            @Valid @RequestBody SubmitSuggestionRequest req,
            @CurrentUser Long userId) {
        return Result.ok(suggestionService.submitSuggestion(userId, req));
    }
}
