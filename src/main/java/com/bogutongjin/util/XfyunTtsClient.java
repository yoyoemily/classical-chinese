package com.bogutongjin.util;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.bogutongjin.config.XfyunTtsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 讯飞长文本语音合成 HTTP Client
 * <p>
 * 流程：提交合成任务 → 轮询查询 → 下载 MP3
 * <p>
 * 鉴权方式：HMAC-SHA256 签名，拼到 URL query params（非 Header），
 * 签名原文格式："host: {host}\ndate: {RFC1123 GMT}\nPOST {path} HTTP/1.1"
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XfyunTtsClient {

    private final XfyunTtsProperties props;

    // ============================================
    // 公开方法
    // ============================================

    /**
     * 合成语音并返回 MP3 字节数组（使用默认发音人）
     *
     * @param text 待合成的纯文本
     * @return MP3 音频字节数组
     */
    public byte[] synthesize(String text) {
        return synthesize(text, null);
    }

    /**
     * 合成语音并返回 MP3 字节数组
     *
     * @param text 待合成的纯文本
     * @param vcn  发音人，为 null 时使用配置中的默认值
     * @return MP3 音频字节数组
     */
    public byte[] synthesize(String text, String vcn) {
        // 1. 创建任务
        String createResp = createTask(text, vcn != null ? vcn : props.getVcn());
        JSONObject createJson = JSONUtil.parseObj(createResp);
        Integer code = getInt(createJson, "header.code");
        if (code == null || code != 0) {
            String msg = getStr(createJson, "header.message");
            throw new RuntimeException("创建讯飞 TTS 任务失败, code=" + code + ", message=" + msg);
        }

        String taskId = getStr(createJson, "header.task_id");
        log.info("[XfyunTTS] 任务已创建, taskId={}", taskId);

        // 2. 轮询直到完成
        String audioUrl = pollUntilComplete(taskId);
        log.info("[XfyunTTS] 合成完成, taskId={}, audioUrl={}", taskId, audioUrl);

        // 3. 下载 MP3
        byte[] audioBytes = downloadAudio(audioUrl);
        log.info("[XfyunTTS] 音频下载完成, taskId={}, size={}KB", taskId, audioBytes.length / 1024);
        return audioBytes;
    }

    // ============================================
    // 步骤1：创建合成任务
    // ============================================

    private String createTask(String text, String vcn) {
        String body = buildCreateRequestBody(text, vcn);
        String url = buildAuthUrl(props.getCreateUrl(), "POST");
        log.debug("[XfyunTTS] 创建任务, text.length={}, vcn={}", text.length(), vcn);
        return doPost(url, body);
    }

    private String buildCreateRequestBody(String text, String vcn) {
        String encodedText = Base64.encode(text);

        JSONObject body = JSONUtil.createObj();

        JSONObject header = JSONUtil.createObj();
        header.set("app_id", props.getAppId());
        header.set("request_id", UUID.randomUUID().toString());
        body.set("header", header);

        JSONObject dts = JSONUtil.createObj();
        dts.set("vcn", vcn);
        dts.set("speed", props.getSpeed());
        dts.set("volume", props.getVolume());
        dts.set("pitch", props.getPitch());
        dts.set("bgs", 0);
        dts.set("reg", 0);
        dts.set("rdn", 0);
        dts.set("rhy", 0);
        dts.set("scn", 0);

        JSONObject pybuf = JSONUtil.createObj();
        pybuf.set("encoding", "utf8");
        pybuf.set("compress", "raw");
        pybuf.set("format", "plain");
        dts.set("pybuf", pybuf);

        JSONObject audio = JSONUtil.createObj();
        audio.set("encoding", "lame");
        audio.set("sample_rate", 16000);
        audio.set("channels", 1);
        audio.set("bit_depth", 16);
        dts.set("audio", audio);

        JSONObject parameter = JSONUtil.createObj();
        parameter.set("dts", dts);
        body.set("parameter", parameter);

        JSONObject textObj = JSONUtil.createObj();
        textObj.set("encoding", "utf8");
        textObj.set("compress", "raw");
        textObj.set("format", "plain");
        textObj.set("text", encodedText);

        JSONObject payload = JSONUtil.createObj();
        payload.set("text", textObj);
        body.set("payload", payload);

        return body.toString();
    }

    // ============================================
    // 步骤2：轮询查询任务状态
    // ============================================

    private String pollUntilComplete(String taskId) {
        String queryBody = buildQueryRequestBody(taskId);
        String queryUrl = buildAuthUrl(props.getQueryUrl(), "POST");

        for (int i = 0; i < props.getPollMaxAttempts(); i++) {
            // 第一次立即查询，后续等间隔再查
            if (i > 0) {
                try {
                    TimeUnit.SECONDS.sleep(props.getPollIntervalSeconds());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("轮询被中断", e);
                }
            }

            String resp = doPost(queryUrl, queryBody);
            JSONObject json = JSONUtil.parseObj(resp);

            Integer code = getInt(json, "header.code");
            if (code == null || code != 0) {
                String msg = getStr(json, "header.message");
                throw new RuntimeException("查询讯飞 TTS 任务失败, code=" + code + ", message=" + msg);
            }

            String taskStatusStr = getStr(json, "header.task_status");
            int taskStatus = Integer.parseInt(taskStatusStr != null ? taskStatusStr : "0");

            log.debug("[XfyunTTS] 轮询 #{}/{} taskId={}, status={}",
                    i + 1, props.getPollMaxAttempts(), taskId, taskStatus);

            if (taskStatus == 5) {
                String audioBase64 = getStr(json, "payload.audio.audio");
                if (audioBase64 == null) {
                    throw new RuntimeException("查询结果中无音频数据, taskId=" + taskId);
                }
                String audioEncoding = getStr(json, "payload.audio.encoding");
                byte[] decoded = Base64.decode(audioBase64);
                String audioUrl = new String(decoded, StandardCharsets.UTF_8);
                log.info("[XfyunTTS] 音频编码={}, audioUrl={}", audioEncoding, audioUrl);
                return audioUrl;
            }
        }
        throw new RuntimeException("讯飞 TTS 任务超时, taskId=" + taskId
                + ", 已轮询 " + props.getPollMaxAttempts() + " 次");
    }

    private String buildQueryRequestBody(String taskId) {
        JSONObject body = JSONUtil.createObj();

        JSONObject header = JSONUtil.createObj();
        header.set("app_id", props.getAppId());
        header.set("task_id", taskId);
        body.set("header", header);

        return body.toString();
    }

    // ============================================
    // 步骤3：下载音频
    // ============================================

    private byte[] downloadAudio(String audioUrl) {
        HttpResponse resp = HttpRequest.get(audioUrl)
                .timeout(30000)
                .execute();
        if (!resp.isOk()) {
            throw new RuntimeException("下载音频失败, url=" + audioUrl
                    + ", status=" + resp.getStatus());
        }
        return resp.bodyBytes();
    }

    // ============================================
    // HMAC-SHA256 鉴权
    // ============================================

    private String buildAuthUrl(String requestUrl, String method) {
        try {
            URL url = new URL(requestUrl);
            String host = url.getHost();
            String path = url.getPath();
            String date = generateRfc1123Date();

            String originSign = StrUtil.format(
                    "host: {}\ndate: {}\n{} {} HTTP/1.1",
                    host, date, method, path);

            String signature = hmacSha256(originSign, props.getApiSecret());

            String authorization = StrUtil.format(
                    "api_key=\"{}\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"{}\"",
                    props.getApiKey(), signature);

            String authEncoded = Base64.encode(authorization);

            return StrUtil.format("{}://{}{}?authorization={}&date={}&host={}",
                    url.getProtocol(), host, path, authEncoded, date, host);
        } catch (Exception e) {
            throw new RuntimeException("构建鉴权 URL 失败: " + requestUrl, e);
        }
    }

    private String generateRfc1123Date() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

    private String hmacSha256(String plainText, String key) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] raw = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encode(raw);
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 签名失败", e);
        }
    }

    // ============================================
    // HTTP 辅助
    // ============================================

    private String doPost(String url, String body) {
        HttpResponse resp = HttpRequest.post(url)
                .body(body, "application/json;charset=utf-8")
                .setConnectionTimeout(10000)
                .setReadTimeout(30000)
                .execute();
        if (!resp.isOk()) {
            throw new RuntimeException("讯飞 TTS HTTP 请求失败, url=" + url
                    + ", status=" + resp.getStatus() + ", body=" + resp.body());
        }
        return resp.body();
    }

    // ============================================
    // JSON 路径提取辅助（Hutool getByPath 只支持 2 参数）
    // ============================================

    private String getStr(JSONObject json, String path) {
        return json.getByPath(path, String.class);
    }

    private Integer getInt(JSONObject json, String path) {
        return json.getByPath(path, Integer.class);
    }
}
