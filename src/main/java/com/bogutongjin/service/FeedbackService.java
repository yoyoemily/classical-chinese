package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bogutongjin.dto.FeedbackDetailResponse;
import com.bogutongjin.dto.FeedbackListItem;
import com.bogutongjin.dto.SubmitFeedbackRequest;
import com.bogutongjin.entity.Feedback;
import com.bogutongjin.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public Map<String, Object> submitFeedback(Long userId, SubmitFeedbackRequest req) {
        Feedback fb = new Feedback();
        fb.setUserId(userId);
        fb.setCategory(req.getCategory());
        fb.setSource(req.getSource());
        fb.setDescription(req.getDescription());

        if (req.getContext() != null) {
            fb.setSentenceId(req.getContext().getSentenceId());
            fb.setWordId(req.getContext().getWordId());
            fb.setArticleId(req.getContext().getArticleId());
            fb.setReadingMode(req.getContext().getReadingMode());
            fb.setClassicId(req.getContext().getClassicId());
            fb.setNodeId(req.getContext().getNodeId());
            fb.setNodeTitle(req.getContext().getNodeTitle());
            fb.setSentenceText(req.getContext().getSentenceText());
            fb.setArticleTitle(req.getContext().getArticleTitle());
            fb.setClassName(req.getContext().getClassName());
        }
        fb.setResolved(0);
        feedbackMapper.insert(fb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(fb.getId()));
        return result;
    }

    /** 用户反馈列表（分页） */
    public Map<String, Object> listMyFeedback(Long userId, int page, int pageSize) {
        Page<Feedback> mpPage = new Page<>(page, pageSize);
        LambdaQueryWrapper<Feedback> wrapper = new LambdaQueryWrapper<Feedback>()
                .eq(Feedback::getUserId, userId)
                .orderByDesc(Feedback::getCreatedAt);
        Page<Feedback> result = feedbackMapper.selectPage(mpPage, wrapper);

        List<FeedbackListItem> list = result.getRecords().stream().map(fb -> {
            FeedbackListItem item = new FeedbackListItem();
            item.setId(fb.getId());
            item.setCategory(fb.getCategory());
            item.setSource(fb.getSource());
            item.setDescription(fb.getDescription());
            item.setNodeTitle(fb.getNodeTitle());
            item.setArticleTitle(fb.getArticleTitle());
            item.setClassName(fb.getClassName());
            item.setResolved(fb.getResolved());
            item.setReply(fb.getReply());
            item.setReadAt(fb.getReadAt());
            item.setCreatedAt(fb.getCreatedAt());
            item.setUpdatedAt(fb.getUpdatedAt());
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("list", list);
        ret.put("total", result.getTotal());
        ret.put("page", page);
        ret.put("pageSize", pageSize);
        ret.put("hasMore", result.getTotal() > (long) page * pageSize);
        return ret;
    }

    /** 反馈详情（校验归属） */
    public FeedbackDetailResponse getFeedbackDetail(Long userId, Long feedbackId) {
        Feedback fb = feedbackMapper.selectById(feedbackId);
        if (fb == null || !userId.equals(fb.getUserId())) {
            throw new RuntimeException("反馈不存在");
        }
        return toDetail(fb);
    }

    /** 标记已读（校验归属） */
    public void markAsRead(Long userId, Long feedbackId) {
        Feedback fb = feedbackMapper.selectById(feedbackId);
        if (fb == null || !userId.equals(fb.getUserId())) {
            throw new RuntimeException("反馈不存在");
        }
        if (fb.getReadAt() == null) {
            fb.setReadAt(LocalDateTime.now());
            feedbackMapper.updateById(fb);
        }
    }

    /** 已处理未读计数 */
    public Map<String, Object> countResolvedUnread(Long userId) {
        int count = feedbackMapper.countResolvedUnread(userId);
        Map<String, Object> ret = new LinkedHashMap<>();
        ret.put("count", count);
        return ret;
    }

    private FeedbackDetailResponse toDetail(Feedback fb) {
        FeedbackDetailResponse d = new FeedbackDetailResponse();
        d.setId(fb.getId());
        d.setCategory(fb.getCategory());
        d.setSource(fb.getSource());
        d.setDescription(fb.getDescription());
        d.setSentenceId(fb.getSentenceId());
        d.setWordId(fb.getWordId());
        d.setArticleId(fb.getArticleId());
        d.setReadingMode(fb.getReadingMode());
        d.setClassicId(fb.getClassicId());
        d.setNodeId(fb.getNodeId());
        d.setNodeTitle(fb.getNodeTitle());
        d.setSentenceText(fb.getSentenceText());
        d.setArticleTitle(fb.getArticleTitle());
        d.setClassName(fb.getClassName());
        d.setResolved(fb.getResolved());
        d.setReply(fb.getReply());
        d.setReadAt(fb.getReadAt());
        d.setCreatedAt(fb.getCreatedAt());
        d.setUpdatedAt(fb.getUpdatedAt());
        return d;
    }
}
