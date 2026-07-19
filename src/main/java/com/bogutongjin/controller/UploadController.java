package com.bogutongjin.controller;

import com.bogutongjin.annotation.CurrentUser;
import com.bogutongjin.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

/**
 * 文件上传控制器
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    /** 允许的图片 MIME 类型 */
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp"
    );

    /** 上传文件大小上限 */
    private static final long MAX_SIZE = 2 * 1024 * 1024; // 2MB

    /** 头像上传子目录 */
    private static final String AVATAR_SUB_DIR = "wyq/avatar";

    @Value("${app.upload-dir:${user.home}/upload}")
    private String uploadDir;

    /**
     * 上传头像
     * POST /api/upload/avatar
     */
    @PostMapping("/avatar")
    public Result<Map<String, Object>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @CurrentUser Long userId) {

        // 1. 非空校验
        if (file == null || file.isEmpty()) {
            return Result.fail(400, "文件不能为空");
        }

        // 2. 大小校验
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(400, "文件大小不能超过 2MB");
        }

        // 3. 类型校验
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            return Result.fail(400, "仅支持 jpg/png/gif/webp/bmp 格式");
        }

        // 4. 确定扩展名
        String ext = switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };

        try {
            // 5. 确保目录存在
            Path avatarDir = Paths.get(uploadDir, AVATAR_SUB_DIR);
            Files.createDirectories(avatarDir);

            // 6. 以 userId_timestamp.ext 命名，新上传覆盖旧头像
            //    可选：先清理该用户之前的头像文件
            String filename = userId + "_" + System.currentTimeMillis() + ext;

            // 7. 写入磁盘
            Path target = avatarDir.resolve(filename);
            file.transferTo(target.toFile());

            // 8. 返回可访问 URL
            String avatarUrl = "https://wyq.yinqueai.com/upload/" + AVATAR_SUB_DIR + "/" + filename;
            return Result.ok(Map.of("avatarUrl", avatarUrl));

        } catch (IOException e) {
            return Result.fail(500, "头像保存失败");
        }
    }
}
