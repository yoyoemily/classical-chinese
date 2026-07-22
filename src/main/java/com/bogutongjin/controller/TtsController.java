package com.bogutongjin.controller;

import com.bogutongjin.common.Result;
import com.bogutongjin.service.TtsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 管理后台 — TTS 语音合成接口（免登录，/api/admin/** 已全局放行）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/tts")
@RequiredArgsConstructor
public class TtsController {

    private final TtsService ttsService;

    /**
     * 合成选篇全文音频
     * <p>
     * 拼接 article_sentence 全部句子 → 调讯飞长文本 TTS → 存到
     * ~/upload/wyq/tts/article/{articleId}.mp3（OSSFS 挂载目录）
     * → 更新 article.full_text_audio_url
     *
     * @param vcn 可选，覆盖默认发音人（如 x4_yezi）
     */
    @PostMapping("/article/{articleId}")
    public Result<Map<String, Object>> synthesizeArticle(
            @PathVariable String articleId,
            @RequestParam(required = false) String vcn) {
        log.info("[TTS] 收到选篇合成请求: articleId={}, vcn={}", articleId, vcn);
        try {
            String summary = ttsService.synthesizeArticle(articleId, vcn);
            return Result.ok(Map.of("success", true, "articleId", articleId, "summary", summary));
        } catch (Exception e) {
            log.error("[TTS] 选篇合成失败: articleId={}", articleId, e);
            return Result.fail(500, e.getMessage());
        }
    }

    /**
     * 合成经典章节音频
     * <p>
     * 拼接 classic_paragraph 全部段落 → 调讯飞长文本 TTS → 存到
     * ~/upload/wyq/tts/classic/{classicId}_{chapterId}.mp3 → 更新 classic_chapter.audio_url
     *
     * @param classicId 经典 ID，用于校验章节是否属于该经典
     * @param vcn       可选，覆盖默认发音人（如 x4_mingge）
     */
    @PostMapping("/classic-chapter/{chapterId}")
    public Result<Map<String, Object>> synthesizeClassicChapter(
            @PathVariable Long chapterId,
            @RequestParam Long classicId,
            @RequestParam(required = false) String vcn) {
        log.info("[TTS] 收到经典章节合成请求: classicId={}, chapterId={}, vcn={}", classicId, chapterId, vcn);
        try {
            String summary = ttsService.synthesizeClassicChapter(classicId, chapterId, vcn);
            return Result.ok(Map.of("success", true, "classicId", classicId, "chapterId", chapterId, "summary", summary));
        } catch (Exception e) {
            log.error("[TTS] 经典章节合成失败: classicId={}, chapterId={}", classicId, chapterId, e);
            return Result.fail(500, e.getMessage());
        }
    }
}
