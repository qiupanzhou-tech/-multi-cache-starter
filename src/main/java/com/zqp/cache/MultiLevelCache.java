package com.zqp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.zqp.consistency.CacheMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 多级缓存编排层
 * L1: Caffeine 本地缓存（纳秒级，进程内，不跨实例）
 * L2: Redis 分布式缓存（毫秒级，跨实例共享）
 *
 * 读取流程: L1 → L2 → 回填L1 → 缓存未命中
 * 写入流程: L1 + L2 双写，带随机 TTL 防雪崩
 */
@Component
public class MultiLevelCache {

    private static final Logger log = LoggerFactory.getLogger(MultiLevelCache.class);

    /** 空值缓存标记：区分"没缓存过"和"缓存过但值是 null"，防止缓存穿透 */
    static final String NULL_MARKER = "__CACHE_NULL__";

    private static final Random RANDOM = new Random();

    @Resource
    private Cache<String, Object> caffeineCache;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private CacheMessagePublisher cacheMessagePublisher;

    // ==================== 读 ====================

    /**
     * 多级缓存读取
     * @return 缓存值，null 表示未命中（调用方需查 DB）
     */
    public Object get(String key) {
        // 1. L1: Caffeine 本地缓存 — 纳秒级，Always available
        Object value = caffeineCache.getIfPresent(key);
        if (value != null) {
            return unwrapNull(value);
        }

        // 2. L2: Redis 分布式缓存 — 降级兜底
        try {
            value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                caffeineCache.put(key, value);
                return unwrapNull(value);
            }
        } catch (Exception e) {
            // Redis 故障 → 返回 null，让上层查 DB（降级但不中断服务）
            log.warn("Redis get failed for key [{}], fallback to DB", key, e);
        }

        return null;
    }

    // ==================== 写 ====================

    /**
     * 写入多级缓存
     * @param baseTtlSeconds 基础过期时间（秒）
     * @return 实际 TTL（添加了随机偏移后的值）
     */
    public long put(String key, Object value, long baseTtlSeconds) {
        long actualTtl = addRandomOffset(baseTtlSeconds);

        Object cacheValue = (value != null) ? value : NULL_MARKER;
        long ttl = (value != null) ? actualTtl : 60;

        // L1 永远可用（进程内存，不依赖外部）
        caffeineCache.put(key, cacheValue);

        // L2 写失败 → 记录日志但不抛异常，L1 已生效
        try {
            redisTemplate.opsForValue().set(key, cacheValue, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis put failed for key [{}], cached in L1 only", key, e);
        }

        return ttl;
    }

    // ==================== 删 ====================

    /**
     * 全量清除缓存：L1 本地 + L2 Redis + 集群广播
     * 由 @CacheEvict 或业务代码主动调用
     */
    public void evict(String key) {
        // 1. 清 L1（本地操作，必定成功）
        caffeineCache.invalidate(key);

        // 2. 清 L2（Redis 故障时不影响 L1 已清的事实）
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis delete failed for key [{}]", key, e);
        }

        // 3. 广播（publish 内部已有 try-catch）
        cacheMessagePublisher.publish(key);
    }

    /**
     * 仅清除本实例 L1 本地缓存，不清 Redis，不广播
     * 由 Redis Pub/Sub 消息监听器调用，避免"收消息 → 清 L2 → 发消息"无限循环
     */
    public void evictLocal(String key) {
        caffeineCache.invalidate(key);
    }

    // ==================== 内部方法 ====================

    /**
     * 解包空值标记：标记存在 → 返回 null（表示缓存命中但值是 null）
     * 如果不是标记 → 原样返回
     */
    private Object unwrapNull(Object value) {
        return NULL_MARKER.equals(value) ? null : value;
    }

    /**
     * 给 TTL 添加 ±10% 随机偏移
     * 原理：如果所有 Key 在同一时间过期，大量请求同时穿透到 DB → 缓存雪崩
     * 随机化后，过期时间分散到不同时刻，避免集中失效
     */
    long addRandomOffset(long baseSeconds) {
        long offset = (long) (baseSeconds * 0.1 * RANDOM.nextDouble());
        // 50% 概率加、50% 概率减
        return RANDOM.nextBoolean() ? baseSeconds + offset : baseSeconds - offset;
    }
}
