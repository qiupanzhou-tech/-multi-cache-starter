package com.zqp.aspect;

import com.zqp.annotation.CacheProtect;
import com.zqp.bloom.BloomFilterService;
import com.zqp.cache.MultiLevelCache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存防护 AOP 切面
 *
 * 执行流程（按顺序）:
 * 1. SpEL 解析注解参数 → 构建缓存 Key
 * 2. [布隆过滤器]  校验 Key 是否可能存在（穿透一重防护）
 * 3. [多级缓存]    L1(Caffeine) → L2(Redis) → 回填 L1
 * 4. [分布式锁]    缓存未命中时加锁，防止热点 Key 并发查库（击穿防护）
 * 5. 双重校验      获取锁后再次查缓存，防止等待锁期间已被另一线程写入
 * 6. 执行原方法    查 DB 获取数据
 * 7. [回写缓存]    写入 L1+L2，TTL 随机偏移（雪崩防护）
 * 8. [降级兜底]    异常时直接执行原方法，不中断业务
 */
@Aspect
@Component
public class CacheProtectAspect {

    private static final Logger log = LoggerFactory.getLogger(CacheProtectAspect.class);

    /** SpEL 表达式解析器 */
    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    /** 参数名发现器：从字节码获取方法参数名 */
    private static final DefaultParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Resource
    private MultiLevelCache cache;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private BloomFilterService bloomFilter;

    @Pointcut("@annotation(com.zqp.annotation.CacheProtect)")
    public void cacheProtectPointcut() {}

    @Around("cacheProtectPointcut()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        CacheProtect annotation = method.getAnnotation(CacheProtect.class);

        // ========== 1. 构建缓存 Key ==========
        String cacheKey = buildCacheKey(annotation, method, pjp.getArgs());

        // ========== 2. 布隆过滤器 — 穿透第一重防护 ==========
        if (annotation.enableBloom() && !bloomFilter.mightContain(cacheKey)) {
            // 布隆判定 Key 一定不存在 → 直接返回，不查缓存、不查 DB
            log.debug("Bloom filter rejected: {}", cacheKey);
            return null;
        }

        // ========== 3. 查询多级缓存（L1→L2） ==========
        Object cached = cache.get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit: {}", cacheKey);
            return cached;
        }

        // ========== 4. 缓存未命中 → 分布式锁防击穿 ==========
        if (!annotation.enableLock()) {
            Object result = pjp.proceed();
            cacheIfNeeded(cacheKey, result, annotation);
            return result;
        }

        String lockKey = "lock:" + cacheKey;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (acquired) {
                try {
                    // 双重校验
                    cached = cache.get(cacheKey);
                    if (cached != null) {
                        log.debug("Cache hit after lock wait: {}", cacheKey);
                        return cached;
                    }

                    // 执行原方法（查 DB）
                    Object result = pjp.proceed();

                    // 回写缓存 + 布隆
                    cacheIfNeeded(cacheKey, result, annotation);

                    return result;
                } finally {
                    lock.unlock();
                }
            } else {
                // 获取锁超时：短暂等待后重试缓存
                log.warn("Lock timeout for: {}", cacheKey);
                Thread.sleep(50);
                cached = cache.get(cacheKey);
                if (cached != null) return cached;
                return pjp.proceed();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return pjp.proceed();
        } catch (Exception e) {
            log.error("Cache aspect error for key: {}", cacheKey, e);
            return pjp.proceed();
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 构建缓存 Key = 注解前缀 + ":" + SpEL 动态后缀
     * 例如: @CacheProtect(key="user:info", keyExpression="#id") → "user:info:123"
     */
    private String buildCacheKey(CacheProtect annotation, Method method, Object[] args) {
        String prefix = annotation.key();
        String keyExpression = annotation.keyExpression();

        if (keyExpression == null || keyExpression.isEmpty()) {
            return prefix;
        }

        String[] paramNames = NAME_DISCOVERER.getParameterNames(method);

        EvaluationContext context = new StandardEvaluationContext();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = SPEL_PARSER.parseExpression(keyExpression);
        Object suffix = expression.getValue(context);

        return prefix + ":" + (suffix != null ? suffix.toString() : "");
    }

    /**
     * 按需写入缓存：处理 null 值缓存策略 + TTL 随机偏移
     */
    private void cacheIfNeeded(String cacheKey, Object result, CacheProtect annotation) {
        if (result == null && !annotation.cacheNull()) {
            return;
        }

        // 真实数据存在时，加入布隆过滤器
        if (result != null && annotation.enableBloom()) {
            bloomFilter.add(cacheKey);
        }

        long ttl = annotation.timeUnit().toSeconds(annotation.ttl());
        cache.put(cacheKey, result, ttl);
    }
}
