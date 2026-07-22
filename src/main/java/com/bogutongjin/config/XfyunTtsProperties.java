package com.bogutongjin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 讯飞长文本语音合成配置属性
 * 对应 application.yml 中的 xfyun.tts 节点
 */
@Data
@Component
@ConfigurationProperties(prefix = "xfyun.tts")
public class XfyunTtsProperties {

    /** 讯飞控制台应用 ID */
    private String appId;

    /** 讯飞控制台 API Key（用于 HMAC-SHA256 签名） */
    private String apiKey;

    /** 讯飞控制台 API Secret（用于 HMAC-SHA256 签名） */
    private String apiSecret;

    /** 发音人，默认 x4_xiaoguo（沉稳男声） */
    private String vcn = "x4_xiaoguo";

    /** 语速，0-100，默认 50 */
    private int speed = 50;

    /** 音量，0-100，默认 50 */
    private int volume = 50;

    /** 音高，0-100，默认 50 */
    private int pitch = 50;

    /** 音频输出根目录（本地路径，通过 OSSFS 挂载到 OSS） */
    private String outputDir = System.getProperty("user.home") + "/upload/wyq/tts";

    /** 选篇音频子目录 */
    private String articleSubDir = "article";

    /** 经典章节音频子目录 */
    private String classicSubDir = "classic";

    /** OSS 公网访问 URL 前缀 */
    private String baseUrl = "https://wyq.yinqueai.com/upload/wyq/tts";

    /** 创建任务 API 地址 */
    private String createUrl = "https://api-dx.xf-yun.com/v1/private/dts_create";

    /** 查询任务 API 地址 */
    private String queryUrl = "https://api-dx.xf-yun.com/v1/private/dts_query";

    /** 轮询间隔（秒） */
    private int pollIntervalSeconds = 5;

    /** 最大轮询次数 */
    private int pollMaxAttempts = 60;
}
