package com.bogutongjin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bogutongjin.common.BusinessException;
import com.bogutongjin.entity.*;
import com.bogutongjin.mapper.*;
import com.bogutongjin.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;
    private final UserWordProgressMapper userWordProgressMapper;
    private final UserCheckinMapper userCheckinMapper;
    private final UserBadgeMapper userBadgeMapper;
    private final UserAnswerHistoryMapper userAnswerHistoryMapper;
    private final DailyTaskMapper dailyTaskMapper;
    private final StudyMistakeMapper studyMistakeMapper;
    private final StudyMistakeSentenceMapper studyMistakeSentenceMapper;
    private final UserAudioListenLogMapper userAudioListenLogMapper;

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

        // 查找是否有冷却中的清除记录
        String recoveryDeadline = getRecoveryDeadline(user);
        if (recoveryDeadline != null) {
            result.put("recoveryDeadline", recoveryDeadline);
        }

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

        // 查找是否有冷却中的清除记录
        String recoveryDeadline = getRecoveryDeadline(user);
        if (recoveryDeadline != null) {
            result.put("recoveryDeadline", recoveryDeadline);
        }

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

    // ============================================
    // 清除学习数据
    // ============================================

    /**
     * 清除学习数据：软删除所有业务数据 → user 旧行打入冷宫 → clone 新 user 行
     *
     * @param userId 当前用户 ID
     * @return { token, userId: newUserId, recoveryDeadline }
     */
    @Transactional
    public Map<String, Object> clearUserData(Long userId) {
        User currentUser = userMapper.selectById(userId);
        if (currentUser == null) {
            throw new BusinessException(10003, "用户不存在");
        }

        // 1. 查同 openId 的冷宫用户（deleted=1，uk_openid_deleted 保证最多一条）
        User coldUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getOpenId, currentUser.getOpenId())
                .eq(User::getDeleted, 1)
        );

        // 2. 冷宫用户存在 → 物理删除冷宫全部数据（不管时间）
        if (coldUser != null) {
            physicalDeleteColdData(coldUser.getId());
        }

        // 3. 软删除当前用户的所有业务数据
        softDeleteAllActiveData(userId);

        // 4. 旧 user 行打入冷宫
        currentUser.setDeleted(1);
        currentUser.setDataClearedAt(LocalDateTime.now());
        userMapper.updateById(currentUser);

        // 5. clone 新 user 行（业务字段全部默认值）
        User newUser = new User();
        newUser.setOpenId(currentUser.getOpenId());
        newUser.setUnionId(currentUser.getUnionId());
        // avatarUrl/nickName/grade/totalXp/currentStreak/longestStreak/memberLevel 全部默认值
        newUser.setDeleted(0);
        userMapper.insert(newUser);

        // 6. 返回新 token + recoveryDeadline
        LocalDateTime deadline = LocalDateTime.now().plusHours(24);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtUtil.generate(newUser.getId()));
        result.put("userId", newUser.getId());
        result.put("recoveryDeadline", deadline.toString());
        return result;
    }

    /**
     * 恢复学习数据：新旧数据 deleted 互换
     *
     * @param currentUserId 清除后 clone 的新 userId
     * @return { token, userId: oldUserId }
     */
    @Transactional
    public Map<String, Object> recoverUserData(Long currentUserId) {
        User currentUser = userMapper.selectById(currentUserId);
        if (currentUser == null) {
            throw new BusinessException(10003, "用户不存在");
        }

        // 查同 openId 的冷宫 user
        User oldUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getOpenId, currentUser.getOpenId())
                .eq(User::getDeleted, 1)
                .isNotNull(User::getDataClearedAt)
        );

        if (oldUser == null) {
            throw new BusinessException(10008, "没有可恢复的数据");
        }
        if (oldUser.getDataClearedAt().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new BusinessException(10009, "已超过 24 小时恢复期限，数据无法恢复");
        }

        // 8 张业务表：旧 userId 的数据 deleted=1→0（复活），新 userId 的数据 deleted=0→1（打入冷宫）
        swapDeletedFlags(oldUser.getId(), currentUser.getId());

        // user 表：三步 swap，用 deleted=2 做中间值绕过 uk_openid_deleted 约束
        // 新行先脱离 deleted=0（设2），旧行从 1 改为 0（复活），新行改为 1（冷宫，设 data_cleared_at 标记）
        currentUser.setDeleted(2);
        userMapper.updateById(currentUser);

        oldUser.setDeleted(0);
        oldUser.setDataClearedAt(null);
        userMapper.updateById(oldUser);

        currentUser.setDeleted(1);
        currentUser.setDataClearedAt(LocalDateTime.now());
        userMapper.updateById(currentUser);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", jwtUtil.generate(oldUser.getId()));
        result.put("userId", oldUser.getId());
        return result;
    }

    // ============================================
    // 私有辅助方法
    // ============================================

    /** 查找同 openId 的冷宫 user 的 24h 恢复截止时间，过期返回 null */
    private String getRecoveryDeadline(User currentUser) {
        User coldUser = userMapper.selectOne(
            new LambdaQueryWrapper<User>()
                .eq(User::getOpenId, currentUser.getOpenId())
                .eq(User::getDeleted, 1)
                .isNotNull(User::getDataClearedAt)
        );
        if (coldUser == null || coldUser.getDataClearedAt() == null) return null;
        LocalDateTime deadline = coldUser.getDataClearedAt().plusHours(24);
        if (deadline.isBefore(LocalDateTime.now())) return null;
        return deadline.toString();
    }

    /** 软删除当前用户所有业务数据（deleted=0 → deleted=1） */
    private void softDeleteAllActiveData(Long userId) {
        userWordProgressMapper.update(null,
            new LambdaUpdateWrapper<UserWordProgress>()
                .eq(UserWordProgress::getUserId, userId)
                .set(UserWordProgress::getDeleted, 1));
        userCheckinMapper.update(null,
            new LambdaUpdateWrapper<UserCheckin>()
                .eq(UserCheckin::getUserId, userId)
                .set(UserCheckin::getDeleted, 1));
        userBadgeMapper.update(null,
            new LambdaUpdateWrapper<UserBadge>()
                .eq(UserBadge::getUserId, userId)
                .set(UserBadge::getDeleted, 1));
        userAnswerHistoryMapper.update(null,
            new LambdaUpdateWrapper<UserAnswerHistory>()
                .eq(UserAnswerHistory::getUserId, userId)
                .set(UserAnswerHistory::getDeleted, 1));
        dailyTaskMapper.update(null,
            new LambdaUpdateWrapper<DailyTask>()
                .eq(DailyTask::getUserId, userId)
                .set(DailyTask::getDeleted, 1));
        studyMistakeMapper.update(null,
            new LambdaUpdateWrapper<StudyMistake>()
                .eq(StudyMistake::getUserId, userId)
                .set(StudyMistake::getDeleted, 1));
        studyMistakeSentenceMapper.update(null,
            new LambdaUpdateWrapper<StudyMistakeSentence>()
                .eq(StudyMistakeSentence::getUserId, userId)
                .set(StudyMistakeSentence::getDeleted, 1));
        userAudioListenLogMapper.update(null,
            new LambdaUpdateWrapper<UserAudioListenLog>()
                .eq(UserAudioListenLog::getUserId, userId)
                .set(UserAudioListenLog::getDeleted, 1));
    }

    /** 物理删除冷宫用户的所有数据（不可恢复） */
    private void physicalDeleteColdData(Long coldUserId) {
        // 使用原生 SQL 绕过 @TableLogic（冷宫数据 deleted=1，@TableLogic 的 delete 只会找 deleted=0）
        String[] tables = {
            "user_word_progress", "user_checkin", "user_badge", "user_answer_history",
            "daily_task", "study_mistake", "study_mistake_sentence", "user_audio_listen_log"
        };
        for (String table : tables) {
            jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", coldUserId);
        }
        jdbcTemplate.update("DELETE FROM `user` WHERE id = ?", coldUserId);
    }

    /** 恢复时 swap：旧 userId 的数据复活（deleted=1→0），新 userId 的数据打入冷宫（deleted=0→1） */
    private void swapDeletedFlags(Long oldUserId, Long newUserId) {
        String[] tables = {
            "user_word_progress", "user_checkin", "user_badge", "user_answer_history",
            "daily_task", "study_mistake", "study_mistake_sentence", "user_audio_listen_log"
        };
        for (String table : tables) {
            // 复活旧数据
            jdbcTemplate.update("UPDATE " + table + " SET deleted = 0 WHERE user_id = ? AND deleted = 1", oldUserId);
            // 冷宫新数据
            jdbcTemplate.update("UPDATE " + table + " SET deleted = 1 WHERE user_id = ? AND deleted = 0", newUserId);
        }
    }

    /** 经验值 → 等级（查阈值表，从高往低匹配） */
    private int calcLevel(int totalXp) {
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalXp >= LEVEL_THRESHOLDS[i]) return i + 1;
        }
        return 1;
    }
}
