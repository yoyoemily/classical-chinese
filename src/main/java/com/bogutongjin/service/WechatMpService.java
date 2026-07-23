package com.bogutongjin.service;

import com.bogutongjin.config.WechatMpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.kefu.WxMpKefuMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.springframework.stereotype.Service;

/**
 * 微信公众号服务号消息处理
 *
 * <p>处理关注事件 → 生成学习码 → 客服消息回复</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WechatMpService {

    private final WxMpService wxMpService;
    private final UserService userService;
    private final WechatMpProperties mpProperties;

    /**
     * 验证微信服务器 URL 有效性
     *
     * @param signature 微信加密签名
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param echostr   随机字符串
     * @return 验证通过返回 echostr，否则返回空
     */
    public String verifyUrl(String signature, String timestamp, String nonce, String echostr) {
        if (wxMpService.checkSignature(timestamp, nonce, signature)) {
            return echostr;
        }
        return "";
    }

    /**
     * 处理微信推送的 XML 消息/事件
     *
     * @param requestBody XML 请求体
     * @param signature   微信加密签名
     * @param timestamp   时间戳
     * @param nonce       随机数
     * @return 回复消息 XML，无需回复时返回空字符串
     */
    public String handleMessage(String requestBody, String signature,
                                 String timestamp, String nonce) {
        // 校验签名
        if (!wxMpService.checkSignature(timestamp, nonce, signature)) {
            log.warn("微信消息签名校验失败");
            return "";
        }

        try {
            WxMpXmlMessage inMessage = WxMpXmlMessage.fromXml(requestBody);

            log.info("收到微信消息: msgType={}, event={}, fromUser={}",
                inMessage.getMsgType(), inMessage.getEvent(), inMessage.getFromUser());

            // 仅处理关注事件
            if ("event".equals(inMessage.getMsgType())
                    && "subscribe".equals(inMessage.getEvent())) {
                return handleSubscribe(inMessage);
            }

            // 其他消息/事件：不回复（微信要求 5s 内回复，否则重试）
            return "";

        } catch (Exception e) {
            log.error("处理微信消息异常", e);
            return "";
        }
    }

    /**
     * 处理用户关注事件：生成学习码 + 发送客服消息
     */
    private String handleSubscribe(WxMpXmlMessage inMessage) {
        String mpOpenId = inMessage.getFromUser();

        // 生成 6 位数字学习码
        String code;
        try {
            code = userService.generateMpCode(mpOpenId);
            log.info("关注用户 {} 生成学习码: {}", mpOpenId, code);
        } catch (Exception e) {
            log.error("生成学习码失败 mpOpenId={}", mpOpenId, e);
            // 失败时也回复安慰文案
            sendKefuMessage(mpOpenId,
                "欢迎关注文言雀！\n\n系统繁忙，请稍后重新关注获取学习码。\n如持续失败，请在文言雀小程序中联系客服。");
            return "";
        }

        // 客服消息回复学习码
        String content = "欢迎关注文言雀！\n\n" +
            "你的学习码：<" + code + ">\n\n" +
            "请在文言雀小程序中输入此码，即可解锁学习。\n" +
            "——学文言，不辛苦。";

        sendKefuMessage(mpOpenId, content);

        // 返回空字符串，不回复被动消息（使用了客服消息异步发送）
        return "";
    }

    /**
     * 通过公众号客服消息接口给用户发送文本
     */
    private void sendKefuMessage(String mpOpenId, String content) {
        try {
            WxMpKefuMessage msg = WxMpKefuMessage.TEXT()
                .toUser(mpOpenId)
                .content(content)
                .build();
            wxMpService.getKefuService().sendKefuMessage(msg);
        } catch (Exception e) {
            log.error("发送客服消息失败 mpOpenId={}", mpOpenId, e);
        }
    }
}
