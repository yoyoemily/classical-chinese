package com.bogutongjin.service;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaCodeLineColor;
import com.bogutongjin.config.WechatMaProperties;
import com.bogutongjin.entity.InviteRecord;
import com.bogutongjin.entity.User;
import com.bogutongjin.mapper.InviteRecordMapper;
import com.bogutongjin.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.io.ClassPathResource;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

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
    private final WechatMaProperties wechatMaProperties;

    @org.springframework.beans.factory.annotation.Value("${invite.member-threshold:5}")
    private int memberThreshold;

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
    private static final int CARD_RADIUS = 24;

    // 头像参数
    private static final int AVATAR_SIZE = 120;
    private static final int AVATAR_X = (POSTER_WIDTH - AVATAR_SIZE) / 2;  // 300
    private static final int AVATAR_Y = 116;
    private static final String LINE_COLOR = "#2e5d3c";  // 与海报主色一致

    // wxacode.getUnlimited 微信侧已有同 scene+page 缓存，合成廉价无需额外缓存

    /**
     * 生成用户专属海报（含小程序码）
     * @param userId 当前用户 ID
     * @return PNG 字节数组
     */
    public byte[] generatePoster(Long userId) {
        try {
            // 0. 查用户（每次实时查询，确保头像/昵称为最新）
            User user = userMapper.selectById(userId);

            // 头像或昵称未设置 → 返回默认海报（不生成小程序码、不合成、不预写 invite_record）
            if (!hasProfile(user)) {
                log.info("用户 {} 未设置头像或昵称，返回默认海报 share-poster.png", userId);
                return loadDefaultPoster();
            }

            // 1. 生成小程序码
            byte[] wxacodeBytes = generateWxacode(userId);

            // 2. 合成海报
            byte[] posterBytes = compositePoster(wxacodeBytes, user);

            // 3. 预写 invite_record（幂等，同一 userId 只写一次）
            ensureInviteRecord(userId);

            return posterBytes;
        } catch (Exception e) {
            log.error("生成海报失败: userId={}", userId, e);
            throw new RuntimeException("海报生成失败，请重试");
        }
    }

    /**
     * 头像和昵称都已设置才合成个性化海报，否则用默认海报
     */
    private boolean hasProfile(User user) {
        return user != null
                && user.getAvatarUrl() != null && !user.getAvatarUrl().isBlank()
                && user.getNickName() != null && !user.getNickName().isBlank();
    }

    /**
     * 读取 classpath 下的默认海报（含通用小程序码，无 scene）
     */
    private byte[] loadDefaultPoster() throws IOException {
        ClassPathResource resource = new ClassPathResource("static/assets/share-poster.png");
        try (InputStream is = resource.getInputStream()) {
            return is.readAllBytes();
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
                scene, LANDING_PAGE, false, wechatMaProperties.getEnvVersion(),
                WXACODE_GEN_SIZE, false, lineColor, true
        );

        log.info("小程序码生成成功: userId={}, scene={}, size={} bytes", userId, scene, bytes.length);
        return bytes;
    }

    /**
     * 将小程序码合成到海报模板上
     */
    private byte[] compositePoster(byte[] wxacodeBytes, User user) throws Exception {
        // 加载模板图（转为 ARGB 全彩画布，避免索引色调色板导致头像/二维码偏色模糊）
        ClassPathResource templateResource = new ClassPathResource("static/assets/share-poster-template.png");
        BufferedImage poster = new BufferedImage(POSTER_WIDTH, POSTER_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        try (InputStream is = templateResource.getInputStream()) {
            BufferedImage raw = ImageIO.read(is);
            if (raw == null) {
                throw new IllegalStateException("海报模板文件无效：static/assets/share-poster-template.png");
            }
            Graphics2D initG = poster.createGraphics();
            initG.drawImage(raw, 0, 0, null);
            initG.dispose();
        }

        Graphics2D posterG2d = poster.createGraphics();
        posterG2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        posterG2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // 头像（圆形裁剪，下载失败静默跳过）
        String avatarUrl = (user != null && user.getAvatarUrl() != null) ? user.getAvatarUrl() : null;
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            try {
                BufferedImage avatar = downloadAndCropCircle(avatarUrl, AVATAR_SIZE);
                // 白色描边
                posterG2d.setColor(Color.WHITE);
                posterG2d.setStroke(new BasicStroke(4f));
                posterG2d.draw(new Ellipse2D.Float(AVATAR_X - 2, AVATAR_Y - 2,
                        AVATAR_SIZE + 4, AVATAR_SIZE + 4));
                posterG2d.setStroke(new BasicStroke(1f));
                posterG2d.drawImage(avatar, AVATAR_X, AVATAR_Y, null);
            } catch (Exception e) {
                log.warn("下载或绘制头像失败，跳过: avatarUrl={}", avatarUrl, e);
            }
        }

        // 昵称 + 邀你打卡
        String nickName = (user != null && user.getNickName() != null && !user.getNickName().isBlank())
                ? user.getNickName() : "学友";
        String inviteText = nickName + " 邀你打卡";
        Font inviteFont = new Font("SansSerif", Font.BOLD, 30);
        posterG2d.setFont(inviteFont);
        posterG2d.setColor(new Color(0x33, 0x33, 0x33));
        FontMetrics inviteFm = posterG2d.getFontMetrics();
        int inviteTextWidth = inviteFm.stringWidth(inviteText);
        int inviteTextX = (POSTER_WIDTH - inviteTextWidth) / 2;
        int inviteTextY = 290;
        posterG2d.drawString(inviteText, inviteTextX, inviteTextY);

        // 加载小程序码并缩放
        BufferedImage wxacode = ImageIO.read(new java.io.ByteArrayInputStream(wxacodeBytes));
        BufferedImage qrScaled = new BufferedImage(QR_DISPLAY_SIZE, QR_DISPLAY_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = qrScaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(wxacode, 0, 0, QR_DISPLAY_SIZE, QR_DISPLAY_SIZE, null);
        g2d.dispose();

        // 画白底圆角卡片
        int cardSize = QR_DISPLAY_SIZE + CARD_PADDING * 2;
        int cardX = QR_X - CARD_PADDING;
        int cardY = QR_Y - CARD_PADDING;
        posterG2d.setColor(Color.WHITE);
        posterG2d.fill(new RoundRectangle2D.Float(cardX, cardY, cardSize, cardSize, CARD_RADIUS, CARD_RADIUS));

        // 贴小程序码
        posterG2d.drawImage(qrScaled, QR_X, QR_Y, null);

        // 二维码下方提示文字
        String hintText = "长按或扫码进入";
        Font hintFont = new Font("SansSerif", Font.PLAIN, 22);
        posterG2d.setFont(hintFont);
        posterG2d.setColor(new Color(0x88, 0x88, 0x88));
        FontMetrics hintFm = posterG2d.getFontMetrics();
        int hintTextWidth = hintFm.stringWidth(hintText);
        int hintTextX = QR_X + QR_DISPLAY_SIZE / 2 - hintTextWidth / 2;
        int hintTextY = QR_Y + QR_DISPLAY_SIZE + CARD_PADDING + 30;
        posterG2d.drawString(hintText, hintTextX, hintTextY);

        posterG2d.dispose();

        // 输出 PNG
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(poster, "PNG", bos);

        return bos.toByteArray();
    }

    /**
     * 下载微信头像并裁剪为圆形
     */
    private BufferedImage downloadAndCropCircle(String avatarUrl, int size) throws Exception {
        // 微信头像 URL 末尾 /132 → /0 取原图（1080px）
        String url = avatarUrl;
        if (url.endsWith("/132")) {
            url = url.replaceAll("/132$", "/0");
        }

        BufferedImage src;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("User-Agent", "ClassicalChinese/1.0");
        try (InputStream is = conn.getInputStream()) {
            src = ImageIO.read(is);
        } finally {
            conn.disconnect();
        }
        if (src == null) throw new IllegalStateException("无法读取头像图片");

        // 缩放为正方形
        BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = scaled.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.drawImage(src, 0, 0, size, size, null);
        g2d.dispose();

        // 圆形裁剪
        BufferedImage circle = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        g2d = circle.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setClip(new Ellipse2D.Float(0, 0, size, size));
        g2d.drawImage(scaled, 0, 0, null);
        g2d.dispose();

        return circle;
    }

    /**
     * 预写 invite_record（生成海报时调用）。
     * 静默处理 DuplicateKeyException，防止并发竞态导致海报生成报错。
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
        try {
            inviteRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            log.info("invite_record 已存在（并发写入），跳过: sceneCode={}", sceneCode);
        }
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
        log.info("用户 {} 推广数+1", inviterUserId);

        // 7. 回填 invite_record（先尝试 UPDATE 预写记录，未命中则 INSERT，兼容卡片分享路径无预写记录的场景）
        String sceneCode = scene;
        if (!scene.startsWith(SCENE_PREFIX)) {
            sceneCode = SCENE_PREFIX + scene;
        }
        boolean inviteUpdated = inviteRecordMapper.update(null,
                new LambdaUpdateWrapper<InviteRecord>()
                        .set(InviteRecord::getInviteeId, inviteeUserId)
                        .set(InviteRecord::getBoundAt, java.time.LocalDateTime.now())
                        .eq(InviteRecord::getSceneCode, sceneCode)) > 0;

        if (!inviteUpdated) {
            // 卡片分享路径没有预写 invite_record，直接 INSERT
            InviteRecord record = new InviteRecord();
            record.setInviterId(inviterUserId);
            record.setInviteeId(inviteeUserId);
            record.setSceneCode(sceneCode);
            record.setSourceType(1);  // 分享卡片
            record.setBoundAt(java.time.LocalDateTime.now());
            try {
                inviteRecordMapper.insert(record);
            } catch (DuplicateKeyException e) {
                // 并发写入场景，已有其他请求先写入，不再重试
                log.info("invite_record 已存在（并发写入），跳过 INSERT: sceneCode={}", sceneCode);
            }
        }

        log.info("邀请绑定成功: inviter={} -> invitee={}, scene={}", inviterUserId, inviteeUserId, sceneCode);
    }

    /**
     * 获取邀请人数
     */
    public long getInviteCount(Long userId) {
        User user = userMapper.selectById(userId);
        return user != null && user.getInvitedCount() != null ? user.getInvitedCount() : 0;
    }

    /**
     * 获取契约会员升级阈值
     */
    public int getMemberThreshold() {
        return memberThreshold;
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
