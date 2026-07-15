package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
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

    /** 获取今日任务 */
    @GetMapping("/today")
    public Result<Map<String, Object>> getTodayTask(
            @RequestParam String wordBookId,
            @RequestParam(required = false) Integer dailyNew,
            @RequestParam(required = false) Integer dailyReview,
            @CurrentUser Long userId) {
        return Result.ok(studyService.getTodayTask(userId, wordBookId, dailyNew, dailyReview));
    }

    /** 提交答题结果 */
    @PostMapping("/answer")
    public Result<Map<String, Object>> submitAnswer(
            @Valid @RequestBody SubmitAnswerRequest req,
            @CurrentUser Long userId) {
        return Result.ok(studyService.submitAnswer(
                userId, req.getWordBookId(), req.getWordId(), req.getSentenceId(),
                req.getSelectedOption(), req.getCorrect(),
                req.getCorrectAnswer(), req.getWrongAnswer()));
    }

    /** 完成今日学习 */
    @PostMapping("/complete")
    public Result<Map<String, Object>> completeStudy(
            @Valid @RequestBody CompleteStudyRequest req,
            @CurrentUser Long userId) {
        return Result.ok(studyService.completeStudy(userId, req.getWordBookId(), req.getCorrectCount(), req.getWrongCount(), req.getXpGained()));
    }

    /** 获取错题本 */
    @GetMapping("/mistakes")
    public Result<Object> getMistakes(
            @RequestParam(required = false) String wordBookId,
            @CurrentUser Long userId) {
        return Result.ok(studyService.getMistakes(userId, wordBookId));
    }

    /** 移除错题 */
    @DeleteMapping("/mistakes/{wordId}")
    public Result<Void> removeMistake(
            @PathVariable String wordId,
            @CurrentUser Long userId) {
        studyService.removeMistake(userId, wordId);
        return Result.ok();
    }
}
