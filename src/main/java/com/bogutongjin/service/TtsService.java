package com.bogutongjin.service;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.config.XfyunTtsProperties;
import com.bogutongjin.entity.Article;
import com.bogutongjin.entity.ArticleSentence;
import com.bogutongjin.entity.ClassicChapter;
import com.bogutongjin.entity.ClassicParagraph;
import com.bogutongjin.mapper.ArticleMapper;
import com.bogutongjin.mapper.ArticleSentenceMapper;
import com.bogutongjin.mapper.ClassicChapterMapper;
import com.bogutongjin.mapper.ClassicParagraphMapper;
import com.bogutongjin.util.XfyunTtsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TTS 语音合成业务编排
 * <p>
 * 负责：从 DB 取原文拼接 → 调讯飞 Client 合成 → 写 MP3 到 OSS 挂载目录 → 更新 DB
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TtsService {

    private final XfyunTtsClient ttsClient;
    private final XfyunTtsProperties props;

    private final ArticleMapper articleMapper;
    private final ArticleSentenceMapper articleSentenceMapper;
    private final ClassicChapterMapper classicChapterMapper;
    private final ClassicParagraphMapper classicParagraphMapper;

    // ============================================
    // 选篇全文合成
    // ============================================

    /**
     * 合成选篇全文音频并更新数据库
     *
     * @param articleId 选篇 ID（如 art_001）
     * @param vcn       可选，覆盖默认发音人
     * @return 结果摘要
     */
    public String synthesizeArticle(String articleId, String vcn) {
        // 1. 验证选篇存在
        Article article = articleMapper.selectById(articleId);
        if (article == null) {
            throw new IllegalArgumentException("选篇不存在: " + articleId);
        }

        // 2. 取全部句子，按 sort_order 拼接全文
        List<ArticleSentence> sentences = articleSentenceMapper.selectList(
                new LambdaQueryWrapper<ArticleSentence>()
                        .eq(ArticleSentence::getArticleId, articleId)
                        .orderByAsc(ArticleSentence::getSortOrder));

        if (sentences.isEmpty()) {
            throw new IllegalArgumentException("选篇无句子数据: " + articleId);
        }

        String fullText = sentences.stream()
                .map(ArticleSentence::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining());

        log.info("[TTS] 选篇 {} 全文 {} 句, 共 {} 字, vcn={}",
                articleId, sentences.size(), fullText.length(),
                vcn != null ? vcn : props.getVcn());

        // 3. 调用讯飞合成
        byte[] mp3Bytes = vcn != null ? ttsClient.synthesize(fullText, vcn) : ttsClient.synthesize(fullText);

        // 4. 写入文件到 OSS 挂载目录
        String filename = articleId + ".mp3";
        String relativePath = props.getArticleSubDir() + "/" + filename;
        File mp3File = writeToFile(mp3Bytes, relativePath);

        // 5. 更新数据库
        String audioUrl = props.getBaseUrl() + "/" + relativePath;
        article.setFullTextAudioUrl(audioUrl);
        articleMapper.updateById(article);

        log.info("[TTS] 选篇 {} 合成完成, file={}, url={}",
                articleId, mp3File.getAbsolutePath(), audioUrl);

        return StrUtil.format("选篇 {} ({}) 合成完成, {}KB, {}",
                articleId, article.getTitle(), mp3Bytes.length / 1024, audioUrl);
    }

    // ============================================
    // 经典章节合成
    // ============================================

    /**
     * 合成经典章节音频并更新数据库
     *
     * @param chapterId 章节主键 ID（classic_chapter 表）
     * @param vcn       可选，覆盖默认发音人
     * @return 结果摘要
     */
    public String synthesizeClassicChapter(Long chapterId, String vcn) {
        // 1. 验证章节存在
        ClassicChapter chapter = classicChapterMapper.selectById(chapterId);
        if (chapter == null) {
            throw new IllegalArgumentException("经典章节不存在: " + chapterId);
        }

        // 2. 取全部段落，按 sort_order 拼接全文
        List<ClassicParagraph> paragraphs = classicParagraphMapper.selectList(
                new LambdaQueryWrapper<ClassicParagraph>()
                        .eq(ClassicParagraph::getChapterId, chapterId)
                        .orderByAsc(ClassicParagraph::getSortOrder));

        if (paragraphs.isEmpty()) {
            throw new IllegalArgumentException("经典章节无段落数据: chapterId=" + chapterId);
        }

        String fullText = paragraphs.stream()
                .map(ClassicParagraph::getText)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.joining());

        log.info("[TTS] 经典章节 chapterId={}, classicId={}, title={}, {} 段, 共 {} 字, vcn={}",
                chapterId, chapter.getClassicId(), chapter.getTitle(),
                paragraphs.size(), fullText.length(),
                vcn != null ? vcn : props.getVcn());

        // 3. 调用讯飞合成
        byte[] mp3Bytes = vcn != null ? ttsClient.synthesize(fullText, vcn) : ttsClient.synthesize(fullText);

        // 4. 写入文件到 OSS 挂载目录
        // 命名规则: {classicId}_{chapterId}.mp3
        String filename = chapter.getClassicId() + "_" + chapterId + ".mp3";
        String relativePath = props.getClassicSubDir() + "/" + filename;
        File mp3File = writeToFile(mp3Bytes, relativePath);

        // 5. 更新数据库
        String audioUrl = props.getBaseUrl() + "/" + relativePath;
        chapter.setAudioUrl(audioUrl);
        classicChapterMapper.updateById(chapter);

        log.info("[TTS] 经典章节 {} ({}) 合成完成, file={}, url={}",
                chapterId, chapter.getTitle(), mp3File.getAbsolutePath(), audioUrl);

        return StrUtil.format("经典章节 {} ({}) 合成完成, {}KB, {}",
                chapterId, chapter.getTitle(), mp3Bytes.length / 1024, audioUrl);
    }

    // ============================================
    // 文件写入
    // ============================================

    /**
     * 将字节数组写入 OSS 挂载目录
     *
     * @param bytes        MP3 字节数组
     * @param relativePath 相对于 outputDir 的路径（如 article/art_001.mp3）
     * @return 写入的文件对象
     */
    private File writeToFile(byte[] bytes, String relativePath) {
        File file = new File(props.getOutputDir(), relativePath);
        // 确保父目录存在
        FileUtil.mkParentDirs(file);
        FileUtil.writeBytes(bytes, file);
        log.info("[TTS] 文件写入完成: {} ({}KB)", file.getAbsolutePath(), bytes.length / 1024);
        return file;
    }
}
