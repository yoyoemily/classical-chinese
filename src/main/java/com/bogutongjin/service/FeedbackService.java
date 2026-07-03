package com.bogutongjin.service;

import com.bogutongjin.dto.SubmitFeedbackRequest;
import com.bogutongjin.entity.Feedback;
import com.bogutongjin.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

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
        }
        fb.setResolved(0);
        feedbackMapper.insert(fb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(fb.getId()));
        return result;
    }
}
