package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
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
            @CurrentUser Long userId) {
        return Result.ok(feedbackService.submitFeedback(userId, req));
    }

    /** 我的反馈列表 */
    @GetMapping
    public Result<Map<String, Object>> listMyFeedback(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(feedbackService.listMyFeedback(userId, page, pageSize));
    }

    /** 反馈详情 */
    @GetMapping("/{id}")
    public Result<?> getFeedbackDetail(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        return Result.ok(feedbackService.getFeedbackDetail(userId, id));
    }

    /** 标记已读 */
    @PutMapping("/{id}/read")
    public Result<Map<String, Object>> markAsRead(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        feedbackService.markAsRead(userId, id);
        return Result.ok(Map.of("success", true));
    }

    /** 已处理未读计数 */
    @GetMapping("/unread-count")
    public Result<Map<String, Object>> getUnreadCount(@CurrentUser Long userId) {
        return Result.ok(feedbackService.countResolvedUnread(userId));
    }
}
