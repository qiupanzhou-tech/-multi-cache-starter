package com.zqp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 缓存清除注解
 * 用于写操作（增/删/改），自动清除相关缓存
 *
 * 使用示例:
 * @CacheEvict(keys = {"user:info:#id", "user:list"})
 * public int deleteUser(Long id) { ... }
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

    /** 要清除的缓存 Key，支持 SpEL 表达式，支持多个 Key 同时清除 */
    String[] keys();
}
