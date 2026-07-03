package com.bogutongjin.common;

/**
 * 认证异常 — 登录态失效或未授权
 */
public class AuthException extends RuntimeException {

    private final int code;

    public AuthException(int code, String message) {
        super(message);
        this.code = code;
    }

    public AuthException(String message) {
        this(10401, message);
    }

    public int getCode() {
        return code;
    }
}
