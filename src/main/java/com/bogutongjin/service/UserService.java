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
    private final RedeemCodeMapper redeemCodeMapper;

    /** 学习码过期天数：30 天不活跃即失效 */
    private static final int CODE_EXPIRE_DAYS = 30;

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

        // 会员状态（含 30 天活跃判断）
        Map<String, Object> memberStatus = buildMemberStatus(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("level", level);
        result.put("title", level <= TITLES.length ? TITLES[level - 1] : "翰林");
        result.put("totalXP", user.getTotalXp());
        result.put("currentStreak", user.getCurrentStreak());
        result.put("memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 0);
        result.put("nickName", user.getNickName() != null ? user.getNickName() : "");
        result.put("avatarUrl", user.getAvatarUrl() != null ? user.getAvatarUrl() : "");
        result.put("codeStatus", memberStatus.get("codeStatus"));

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

    // ============================================
    // 学习码验证
    // ============================================

    /**
     * 验证学习码：校验码存在、status=0、码属于当前用户或待认领。
     * 公众号生成的码（userId=NULL）→ 认领给当前用户。
     * 验证通过后设为 status=1，记录 verified_at。
     * 同时将同一用户的其他过期码（status=1 但 30 天不活跃）标记为 status=2。
     */
    @Transactional
    public Map<String, Object> verifyCode(Long userId, String code) {
        // 1. 查找该码
        RedeemCode redeemCode = redeemCodeMapper.selectOne(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getCode, code)
        );

        if (redeemCode == null) {
            throw new BusinessException(10004, "学习码不存在");
        }

        // 2. 校验归属
        if (redeemCode.getUserId() == null) {
            // 公众号生成的码，还没有绑定用户 → 认领
            redeemCode.setUserId(userId);
        } else if (!redeemCode.getUserId().equals(userId)) {
            throw new BusinessException(10005, "该学习码不属于您的账号");
        }

        // 3. 校验状态
        if (redeemCode.getStatus() == 2) {
            throw new BusinessException(10006, "该学习码已过期，请重新关注公众号获取");
        }
        if (redeemCode.getStatus() == 1) {
            // 已验证过，直接返回成功（幂等）
            User user = userMapper.selectById(userId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", true);
            result.put("memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 0);
            return result;
        }

        // 4. status=0 → 验证通过
        redeemCode.setStatus(1);
        redeemCode.setVerifiedAt(LocalDateTime.now());
        redeemCodeMapper.updateById(redeemCode);

        // 5. 将同一用户的其他过期有效码标记为过期（status=1 → status=2）
        User user = userMapper.selectById(userId);
        boolean codeActive = isUserActive(user);
        if (!codeActive) {
            // 将旧的已验证码全部标记为过期
            List<RedeemCode> oldCodes = redeemCodeMapper.selectList(
                new LambdaQueryWrapper<RedeemCode>()
                    .eq(RedeemCode::getUserId, userId)
                    .eq(RedeemCode::getStatus, 1)
                    .ne(RedeemCode::getId, redeemCode.getId())
            );
            for (RedeemCode old : oldCodes) {
                old.setStatus(2);
                redeemCodeMapper.updateById(old);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("valid", true);
        result.put("memberLevel", user.getMemberLevel() != null ? user.getMemberLevel() : 0);
        return result;
    }

    /**
     * 获取会员状态快照：memberLevel（永不失效）+ codeVerified + codeActive（30 天活跃窗口）。
     * 运行时判断 30 天不活跃 → 自动将码标记为 status=2。
     */
    public Map<String, Object> getMemberStatus(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(10003, "用户不存在");

        return buildMemberStatus(user);
    }

    /** 构建会员状态（不含用户不存在检查） */
    private Map<String, Object> buildMemberStatus(User user) {
        int memberLevel = user.getMemberLevel() != null ? user.getMemberLevel() : 0;

        // -1=从没绑过码  1=码有效  2=码已过期
        int codeStatus = 0;
        // 先看有没有验证过的码
        boolean hasActive = hasVerifiedCode(user.getId());
        if (hasActive) {
            codeStatus = 1;
        } else {
            // 再看有没有过期码（status=2）
            boolean hasExpired = hasExpiredCode(user.getId());
            if (hasExpired) {
                codeStatus = 2;
            } else {
                // 既没有 status=1 也没有 status=2 → 从没绑过
                codeStatus = -1;
            }
        }

        // 活跃检查：30 天不活跃 → 码失效，status=1 → 2
        if (codeStatus == 1 && !isUserActive(user)) {
            expireUserCodes(user.getId());
            codeStatus = 2;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("memberLevel", memberLevel);
        result.put("codeStatus", codeStatus);
        result.put("lastActiveAt", user.getLastActiveAt() != null ? user.getLastActiveAt().toString() : null);
        return result;
    }

    /** 判断用户是否在 30 天活跃窗口内 */
    private boolean isUserActive(User user) {
        if (user.getLastActiveAt() == null) return false;
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(CODE_EXPIRE_DAYS);
        return user.getLastActiveAt().isAfter(thirtyDaysAgo);
    }

    /** 将用户所有已验证码（status=1）标记为过期（status=2） */
    private void expireUserCodes(Long userId) {
        List<RedeemCode> activeCodes = redeemCodeMapper.selectList(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getUserId, userId)
                .eq(RedeemCode::getStatus, 1)
        );
        for (RedeemCode c : activeCodes) {
            c.setStatus(2);
            redeemCodeMapper.updateById(c);
        }
    }

    /** 是否有已验证码（status=1） */
    private boolean hasVerifiedCode(Long userId) {
        return redeemCodeMapper.selectCount(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getUserId, userId)
                .eq(RedeemCode::getStatus, 1)
        ) > 0;
    }

    /** 是否有已过期码（status=2），区分"从未绑过"和"过期了" */
    private boolean hasExpiredCode(Long userId) {
        return redeemCodeMapper.selectCount(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getUserId, userId)
                .eq(RedeemCode::getStatus, 2)
        ) > 0;
    }

    // ============================================
    // 管理端：生成学习码
    // ============================================

    /**
     * 管理端生成学习码（不绑定用户，用户在小程序输入后通过 verifyCode() 认领）。
     * 服务号审核通过前可手动调用此方法生成测试码。
     *
     * @return 生成的兑换码（6 位数字）
     */
    @Transactional
    public String generateCode() {
        String code = generateUniqueCode();

        RedeemCode entity = new RedeemCode();
        entity.setCode(code);
        entity.setUserId(null);
        entity.setStatus(0);
        redeemCodeMapper.insert(entity);

        return code;
    }

    /**
     * 公众号关注回调生成学习码。
     * userId 初始为 NULL，等用户在小程序输入后通过 verifyCode() 认领。
     *
     * @param mpOpenId 公众号 OpenID
     * @return 生成的兑换码（6 位数字）
     */
    @Transactional
    public String generateMpCode(String mpOpenId) {
        // 1. 将该公众号用户所有未使用码标记为过期
        List<RedeemCode> unusedCodes = redeemCodeMapper.selectList(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getMpOpenId, mpOpenId)
                .eq(RedeemCode::getStatus, 0)
        );
        for (RedeemCode c : unusedCodes) {
            c.setStatus(2);
            redeemCodeMapper.updateById(c);
        }

        // 2. 生成新码
        String code = generateUniqueCode();

        // 3. 插入（userId=NULL，等小程序端输入时认领）
        RedeemCode entity = new RedeemCode();
        entity.setCode(code);
        entity.setUserId(null);
        entity.setMpOpenId(mpOpenId);
        entity.setStatus(0);
        redeemCodeMapper.insert(entity);

        return code;
    }

    /** 生成 6 位随机数字 */
    private String generateUniqueCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    // ============================================
    // 签署契约
    // ============================================

    /** 签订金石契约——前置条件：必须有已验证的学习码（status=1） */
    @Transactional
    public void signPact(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return;

        // 已是会员，幂等返回
        if (user.getMemberLevel() != null && user.getMemberLevel() >= 1) return;

        // 前置条件：必须有已验证的学习码
        RedeemCode code = redeemCodeMapper.selectOne(
            new LambdaQueryWrapper<RedeemCode>()
                .eq(RedeemCode::getUserId, userId)
                .eq(RedeemCode::getStatus, 1)
                .orderByDesc(RedeemCode::getVerifiedAt)
                .last("LIMIT 1")
        );
        if (code == null) {
            throw new BusinessException(10007, "请先关注公众号获取学习码");
        }

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
