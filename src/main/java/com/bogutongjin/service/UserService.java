package com.bogutongjin.service;

import com.bogutongjin.entity.User;
import com.bogutongjin.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    /** 等级称号映射 */
    private static final String[] TITLES = {
        "童生", "秀才", "举人", "贡士", "进士", "探花", "榜眼", "状元", "翰林"
    };

    public Map<String, Object> getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        int level = calcLevel(user.getTotalXp());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("title", level < TITLES.length ? TITLES[level] : "翰林");
        result.put("totalXP", user.getTotalXp());
        result.put("currentStreak", user.getCurrentStreak());
        return result;
    }

    public Map<String, Object> getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("nickName", user.getNickName());
        result.put("grade", user.getGrade());
        return result;
    }

    public void saveUserInfo(Long userId, String avatarUrl, String nickName, String grade) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        user.setAvatarUrl(avatarUrl);
        user.setNickName(nickName);
        user.setGrade(grade);
        userMapper.updateById(user);
    }

    /** 经验值 → 等级 (每 100 XP 升一级) */
    private int calcLevel(int totalXp) {
        return Math.min(totalXp / 100, TITLES.length);
    }
}
