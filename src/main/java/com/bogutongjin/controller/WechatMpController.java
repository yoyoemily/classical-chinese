package com.bogutongjin.controller;

import com.bogutongjin.service.WechatMpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 微信公众号服务号消息回调接口
 *
 * <p>接收微信服务器推送的 XML 消息/事件，需在 MP 后台配置服务器地址为该接口</p>
 * <p>路径：/api/wechat/mp/portal，不走登录拦截器（微信服务器无 JWT）</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/wechat/mp")
@RequiredArgsConstructor
public class WechatMpController {

    private final WechatMpService mpService;

    /**
     * 微信服务器 URL 有效性验证（GET 请求）
     */
    @GetMapping("/portal")
    public String verify(
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce,
            @RequestParam("echostr") String echostr) {
        log.info("微信服务器验证请求: signature={}, timestamp={}, nonce={}", signature, timestamp, nonce);
        return mpService.verifyUrl(signature, timestamp, nonce, echostr);
    }

    /**
     * 接收微信服务器推送的消息/事件（POST 请求）
     */
    @PostMapping(value = "/portal", produces = "application/xml;charset=UTF-8")
    public String handleMessage(
            @RequestBody String requestBody,
            @RequestParam("signature") String signature,
            @RequestParam("timestamp") String timestamp,
            @RequestParam("nonce") String nonce) {
        log.debug("收到微信消息推送: {}", requestBody);
        return mpService.handleMessage(requestBody, signature, timestamp, nonce);
    }
}
