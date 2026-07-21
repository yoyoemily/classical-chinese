package com.bogutongjin.service;

import com.bogutongjin.dto.SubmitSuggestionRequest;
import com.bogutongjin.entity.Suggestion;
import com.bogutongjin.mapper.SuggestionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final SuggestionMapper suggestionMapper;

    public Map<String, Object> submitSuggestion(Long userId, SubmitSuggestionRequest req) {
        Suggestion s = new Suggestion();
        s.setUserId(userId);
        s.setContent(req.getContent());
        s.setContact(req.getContact());
        s.setCategory(req.getCategory());
        suggestionMapper.insert(s);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", String.valueOf(s.getId()));
        return result;
    }
}
