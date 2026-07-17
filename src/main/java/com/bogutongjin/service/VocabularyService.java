package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bogutongjin.entity.UserWordProgress;
import com.bogutongjin.entity.WordBook;
import com.bogutongjin.entity.WordBookEntry;
import com.bogutongjin.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VocabularyService {

    private final UserWordProgressMapper userWordProgressMapper;
    private final WordBookEntryMapper wordBookEntryMapper;
    private final WordBookMapper wordBookMapper;

    public Map<String, Object> getVocabulary(Long userId, String wordBookId, String tab) {
        WordBook book = wordBookMapper.selectById(wordBookId);
        if (book == null) return emptyResult();

        List<UserWordProgress> allProgress = userWordProgressMapper.selectList(
                new LambdaQueryWrapper<UserWordProgress>()
                        .eq(UserWordProgress::getUserId, userId)
                        .eq(UserWordProgress::getWordBookId, wordBookId));

        List<WordBookEntry> entries = wordBookEntryMapper.selectList(
                new LambdaQueryWrapper<WordBookEntry>().eq(WordBookEntry::getWordBookId, wordBookId));

        Map<String, WordBookEntry> entryMap = entries.stream().collect(Collectors.toMap(WordBookEntry::getId, e -> e));

        List<Map<String, Object>> items = new ArrayList<>();
        for (UserWordProgress wp : allProgress) {
            WordBookEntry e = entryMap.get(wp.getEntryId());
            if (e == null) continue;

            String masteryLevel = calcMastery(wp);

            if (!"all".equals(tab)) {
                String mapped = mapTab(tab);
                if (!mapped.equals(masteryLevel)) continue;
            }

            int progress = "done".equals(wp.getStage()) ? 100 : Math.round((parseStage(wp.getStage()) / 6.0f) * 100);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("wordId", e.getId());
            item.put("character", e.getCharacter());
            item.put("pinyin", e.getPinyin());
            item.put("masteryLevel", masteryLevel);
            item.put("progress", progress);
            item.put("stage", wp.getStage());
            items.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", items);
        result.put("total", items.size());
        result.put("page", 1);
        result.put("pageSize", 20);
        result.put("hasMore", false);
        return result;
    }

    private String calcMastery(UserWordProgress wp) {
        if ("done".equals(wp.getStage())) return "mastered";
        if (wp.getResetCount() >= 3) return "difficult";
        if (wp.getWrongCount() >= 2) return "unclear";
        int stage = parseStage(wp.getStage());
        if (stage >= 3) return "familiar";
        return "unclear";
    }

    private int parseStage(String stage) {
        if (stage == null || "done".equals(stage)) return 6;
        try { return Integer.parseInt(stage); } catch (NumberFormatException e) { return 0; }
    }

    private String mapTab(String tab) {
        return switch (tab) {
            case "difficult" -> "difficult";
            case "unclear" -> "unclear";
            case "familiar" -> "familiar";
            case "mastered" -> "mastered";
            default -> "all";
        };
    }

    private Map<String, Object> emptyResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", List.of());
        result.put("total", 0);
        result.put("page", 1);
        result.put("pageSize", 20);
        result.put("hasMore", false);
        return result;
    }
}
