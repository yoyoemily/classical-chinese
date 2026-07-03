package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.dto.CompleteStudyRequest;
import com.bogutongjin.dto.SubmitAnswerRequest;
import com.bogutongjin.service.StudyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/study")
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    /**
     * 获取今日任务
     * GET /api/study/today?wordBookId=xxx&dailyNew=10&dailyReview=5
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayTask(
            @RequestParam String wordBookId,
            @RequestParam(required = false) Integer dailyNew,
            @RequestParam(required = false) Integer dailyReview,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(studyService.getTodayTask(userId, wordBookId, dailyNew, dailyReview));
    }

    /**
     * 提交答题结果
     * POST /api/study/answer
     */
    @PostMapping("/answer")
    public Result<Map<String, Object>> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest req,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(studyService.submitAnswer(
                userId, req.getWordBookId(), req.getWordId(), req.getSentenceId(),
                req.getSelectedOption(), req.getCorrect()));
    }

    /**
     * 完成今日学习
     * POST /api/study/complete
     */
    @PostMapping("/complete")
    public Result<Map<String, Object>> completeStudy(
            @Valid @RequestBody CompleteStudyRequest req,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return Result.ok(studyService.completeStudy(userId, req.getWordBookId(), req.getCorrectCount(), req.getWrongCount()));
    }
}
