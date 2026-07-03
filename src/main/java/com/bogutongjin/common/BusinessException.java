package com.bogutongjin.common;

import lombok.Getter;

/**
 * 业务异常，可指定错误码
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(10001, message);
    }
}
