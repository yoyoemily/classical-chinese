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

    /** 每级最低 XP 门槛（与 TITLES 一一对应） */
    private static final int[] LEVEL_THRESHOLDS = {
        0, 1000, 2000, 3000, 5000, 10000, 20000, 30000, 50000
    };

    public Map<String, Object> getUserProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        int level = calcLevel(user.getTotalXp());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("title", level <= TITLES.length ? TITLES[level - 1] : "翰林");
        result.put("totalXP", user.getTotalXp());
        result.put("currentStreak", user.getCurrentStreak());
        result.put("memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 0);
        result.put("nickName", user.getNickName() != null ? user.getNickName() : "");
        result.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        return result;
    }

    public Map<String, Object> getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("avatarUrl", user.getAvatarUrl());
        result.put("nickName", user.getNickName());
        result.put("grade", user.getGrade());
        result.put("memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 0);
        return result;
    }

    public void saveUserInfo(Long userId, String avatarUrl, String nickName, String grade) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        if (avatarUrl != null && !avatarUrl.isEmpty()) user.setAvatarUrl(avatarUrl);
        if (nickName != null && !nickName.isEmpty()) user.setNickName(nickName);
        if (grade != null) user.setGrade(grade);  // grade 允许设为空字符串（"不设置"）
        userMapper.updateById(user);
    }

    /** 签订金石契约 */
    public void signPact(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;
        user.setMemberLevel(1);
        userMapper.updateById(user);
    }

    /** 经验值 → 等级（查阈值表，从高往低匹配） */
    private int calcLevel(int totalXp) {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalXp >= LEVEL_THRESHOLDS[i]) return i + 1;
        }
        return 1;
    }
}
