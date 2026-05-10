package com.zqp.annotation;

import com.zqp.config.MultiCacheAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 启用多级缓存防护组件
 *
 * 加在 SpringBoot 启动类或任意 @Configuration 类上，
 * 显式导入自动配置，激活 @CacheProtect / @CacheEvict 注解能力。
 *
 * 使用方式:
 * @SpringBootApplication
 * @EnableMultiCache
 * public class Application { ... }
 *
 * 原理：@Import 手动引入配置类，配合 spring.factories 自动装配，
 * 两种方式互为兜底——显式引入优先级更高，自动装配保底。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(MultiCacheAutoConfiguration.class)
public @interface EnableMultiCache {
}
