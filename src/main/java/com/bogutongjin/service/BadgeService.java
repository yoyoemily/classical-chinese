package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bogutongjin.entity.Badge;
import com.bogutongjin.entity.UserBadge;
import com.bogutongjin.mapper.BadgeMapper;
import com.bogutongjin.mapper.UserBadgeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeMapper badgeMapper;
    private final UserBadgeMapper userBadgeMapper;

    public Map<String, Object> getBadges(Long userId) {
        List<Badge> allBadges = badgeMapper.selectList(null);

        List<UserBadge> userBadges = userBadgeMapper.selectList(
                new LambdaQueryWrapper<UserBadge>().eq(UserBadge::getUserId, userId));

        List<Map<String, Object>> badgeList = allBadges.stream().map(b -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", b.getId());
            m.put("name", b.getName());
            m.put("description", b.getDescription());
            m.put("icon", b.getIcon());
            m.put("category", b.getCategory());
            Map<String, Object> cond = new LinkedHashMap<>();
            cond.put("type", b.getConditionType());
            cond.put("value", b.getConditionValue());
            m.put("condition", cond);
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> ubList = userBadges.stream().map(ub -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("badgeId", ub.getBadgeId());
            m.put("earnedDate", ub.getEarnedDate() != null ? ub.getEarnedDate().toString() : "");
            m.put("notified", ub.getNotified() == 1);
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("badges", badgeList);
        result.put("userBadges", ubList);
        return result;
    }
}
