package com.bogutongjin.annotation;

import java.lang.annotation.*;

/**
 * 标记 Controller 方法参数，自动注入当前登录用户 ID
 *
 * <pre>
 *   public Result<?> getProfile(@CurrentUser Long userId) { ... }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
