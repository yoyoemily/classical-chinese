package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.dto.SubmitFeedbackRequest;
import com.bogutongjin.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public Result<Map<String, Object>> submitFeedback(
            @Valid @RequestBody SubmitFeedbackRequest req,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(feedbackService.submitFeedback(userId, req));
    }
}
