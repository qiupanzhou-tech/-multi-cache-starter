package com.zqp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存防护注解
 * 零侵入接入：加在任意 Service/Controller 方法上即可获得多级缓存能力
 *
 * 使用示例:
 * @CacheProtect(key = "user:info", keyExpression = "#id", ttl = 1800)
 * public User getUserById(Long id) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheProtect {

    /** 缓存 Key 前缀，通常为业务标识，如 "user:info"、"goods:detail" */
    String key();

    /** SpEL 表达式，动态拼装 Key 后缀，如 "#id"、#user.id"。
     *  如果不需动态后缀（如全表缓存），留空即可。 */
    String keyExpression() default "";

    /** 缓存过期时间 */
    long ttl() default 1800;

    /** 时间单位，默认秒 */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /** 是否启用布隆过滤器前置拦截（穿透第一重防护） */
    boolean enableBloom() default true;

    /** 是否启用分布式锁防击穿 */
    boolean enableLock() default true;

    /** 是否缓存 null 值（穿透第二重兜底） */
    boolean cacheNull() default true;

    /** 是否随机化 TTL（雪崩防护） */
    boolean randomTtl() default true;
}
