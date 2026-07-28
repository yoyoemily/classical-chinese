package com.bogutongjin.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaCodeLineColor;
import com.bogutongjin.entity.InviteRecord;
import com.bogutongjin.entity.User;
import com.bogutongjin.mapper.InviteRecordMapper;
import com.bogutongjin.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邀请服务 — 小程序码生成、海报合成、邀请关系绑定
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InviteService {

    private final WxMaService wxMaService;
    private final UserMapper userMapper;
    private final InviteRecordMapper inviteRecordMapper;

    private static final String SCENE_PREFIX = "i_";
    private static final String LANDING_PAGE = "pages/index/index";

    // 海报尺寸
    private static final int POSTER_WIDTH = 720;
    private static final int POSTER_HEIGHT = 1280;

    // 小程序码参数：生成 430px，再缩放到 220px
    private static final int WXACODE_GEN_SIZE = 430;
    private static final int QR_DISPLAY_SIZE = 220;

    // 小程序码在海报上的位置（对应 template 中 qr center = (360, 940)）
    private static final int QR_X = 250;  // 360 - 220/2
    private static final int QR_Y = 830;  // 940 - 220/2

    // 白底圆角卡片
    private static final int CARD_PADDING = 16;
    private static final int CARD_RADIUS = 16;
    private static final String LINE_COLOR = "#2e5d3c";  // 与海报主色一致

    // 内存缓存（1 小时 TTL）
    private final Map<Long, byte[]> posterCache = new ConcurrentHashMap<>();
    private final Map<Long, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 3600_000L;

    /**
     * 生成用户专属海报（含小程序码）
     * @param userId 当前用户 ID
     * @return PNG 字节数组
     */
    public byte[] generatePoster(Long userId) {
        // 查缓存
        Long ts = cacheTimestamps.get(userId);
        if (ts != null && System.currentTimeMillis() - ts < CACHE_TTL_MS) {
            byte[] cached = posterCache.get(userId);
            if (cached != null) return cached;
        }

        try {
            // 1. 生成小程序码
            byte[] wxacodeBytes = generateWxacode(userId);

            // 2. 合成海报
            byte[] posterBytes = compositePoster(wxacodeBytes);

            // 3. 预写 invite_record（幂等，同一 userId 只写一次）
            ensureInviteRecord(userId);

            // 4. 缓存
            posterCache.put(userId, posterBytes);
            cacheTimestamps.put(userId, System.currentTimeMillis());

            return posterBytes;
        } catch (Exception e) {
            log.error("生成海报失败: userId={}", userId, e);
            throw new RuntimeException("海报生成失败，请重试");
        }
    }

    /**
     * 调用 wxacode.getUnlimited 生成小程序码
     */
    private byte[] generateWxacode(Long userId) throws Exception {
        String scene = SCENE_PREFIX + userId;

        // 解析颜色
        int r = Integer.parseInt(LINE_COLOR.substring(1, 3), 16);
        int g = Integer.parseInt(LINE_COLOR.substring(3, 5), 16);
        int b = Integer.parseInt(LINE_COLOR.substring(5, 7), 16);
        WxMaCodeLineColor lineColor = new WxMaCodeLineColor(String.valueOf(r), String.valueOf(g), String.valueOf(b));

        byte[] bytes = wxMaService.getQrcodeService().createWxaCodeUnlimitBytes(
                scene, LANDING_PAGE, false, "release",
                WXACODE_GEN_SIZE, false, lineColor, true
        );

        log.info("小程序码生成成功: userId={}, scene={}, size={} bytes", userId, scene, bytes.length);
        return bytes;
    }

    /**
     * 将小程序码合成到海报模板上
     */
    private byte[] compositePoster(byte[] wxacodeBytes) throws Exception {
        // 加载模板图
        ClassPathResource templateResource = new ClassPathResource("static/assets/share-poster-template.png");
        BufferedImage poster;
        try (InputStream is = templateResource.getInputStream()) {
            poster = ImageIO.read(is);
        }
        if (poster == null) {
            throw new IllegalStateException("海报模板文件无效：static/assets/share-poster-template.png");
        }

        // 加载小程序码并缩放
        BufferedImage wxacode = ImageIO.read(new java.io.ByteArrayInputStream(wxacodeBytes));
        BufferedImage qrScaled = new BufferedImage(QR_DISPLAY_SIZE, QR_DISPLAY_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = qrScaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(wxacode, 0, 0, QR_DISPLAY_SIZE, QR_DISPLAY_SIZE, null);
        g2d.dispose();

        // 合成到海报
        Graphics2D posterG2d = poster.createGraphics();
        posterG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 画白底圆角卡片
        int cardSize = QR_DISPLAY_SIZE + CARD_PADDING * 2;
        int cardX = QR_X - CARD_PADDING;
        int cardY = QR_Y - CARD_PADDING;
        posterG2d.setColor(Color.WHITE);
        posterG2d.fill(new RoundRectangle2D.Float(cardX, cardY, cardSize, cardSize, CARD_RADIUS, CARD_RADIUS));

        // 贴小程序码
        posterG2d.drawImage(qrScaled, QR_X, QR_Y, null);
        posterG2d.dispose();

        // 输出 PNG
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(poster, "PNG", bos);

        return bos.toByteArray();
    }

    /**
     * 预写 invite_record（生成海报时调用）
     * scene_code 唯一索引保证幂等
     */
    private void ensureInviteRecord(Long userId) {
        String sceneCode = SCENE_PREFIX + userId;
        // 查是否已存在
        Long count = inviteRecordMapper.selectCount(
                new LambdaQueryWrapper<InviteRecord>()
                        .eq(InviteRecord::getSceneCode, sceneCode));
        if (count > 0) return;

        InviteRecord record = new InviteRecord();
        record.setInviterId(userId);
        record.setSceneCode(sceneCode);
        record.setSourceType(0);  // 海报扫码
        inviteRecordMapper.insert(record);
    }

    /**
     * 绑定邀请关系（登录时调用）
     * 一个事务：写 invitee.invited_by + inviter.invited_count +1 + invite_record 回填
     *
     * @param inviteeUserId 被邀请人（新用户）ID
     * @param scene         小程序码 scene 值，格式 "i_{inviterUserId}"
     */
    @Transactional
    public void bindInviter(Long inviteeUserId, String scene) {
        // 1. 解析邀请人 ID
        Long inviterUserId = parseInviterFromScene(scene);
        if (inviterUserId == null) {
            log.warn("无法解析 scene 中的邀请人: {}", scene);
            return;
        }

        // 2. 防止自己邀请自己
        if (inviterUserId.equals(inviteeUserId)) {
            log.info("用户 {} 扫描了自己的邀请码，跳过绑定", inviteeUserId);
            return;
        }

        // 3. 查 invitee 是否已有上级
        User invitee = userMapper.selectById(inviteeUserId);
        if (invitee == null) return;
        if (invitee.getInvitedBy() != null) {
            log.info("用户 {} 已有邀请人 {}，跳过绑定", inviteeUserId, invitee.getInvitedBy());
            return;
        }

        // 4. 更新 invitee.invited_by（带 AND invited_by IS NULL 防并发）
        boolean updated = userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .set(User::getInvitedBy, inviterUserId)
                        .eq(User::getId, inviteeUserId)
                        .isNull(User::getInvitedBy)) > 0;
        if (!updated) {
            log.info("用户 {} invited_by 已被其他请求写入，跳过", inviteeUserId);
            return;
        }

        // 5. 更新 inviter.invited_count +1
        userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .setSql("invited_count = invited_count + 1")
                        .eq(User::getId, inviterUserId));

        // 6. 回填 invite_record 的 invitee_id
        String sceneCode = scene;
        if (!scene.startsWith(SCENE_PREFIX)) {
            sceneCode = SCENE_PREFIX + scene;
        }
        inviteRecordMapper.update(null,
                new LambdaUpdateWrapper<InviteRecord>()
                        .set(InviteRecord::getInviteeId, inviteeUserId)
                        .set(InviteRecord::getBoundAt, java.time.LocalDateTime.now())
                        .eq(InviteRecord::getSceneCode, sceneCode));

        log.info("邀请绑定成功: inviter={} -> invitee={}, scene={}", inviterUserId, inviteeUserId, sceneCode);
    }

    /**
     * 获取邀请人数
     */
    public long getInviteCount(Long userId) {
        return inviteRecordMapper.selectCount(
                new LambdaQueryWrapper<InviteRecord>()
                        .eq(InviteRecord::getInviterId, userId));
    }

    /**
     * 从 scene 解析邀请人 userId
     */
    private Long parseInviterFromScene(String scene) {
        if (scene == null) return null;
        if (scene.startsWith(SCENE_PREFIX)) {
            try {
                return Long.parseLong(scene.substring(2));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        try {
            return Long.parseLong(scene);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
