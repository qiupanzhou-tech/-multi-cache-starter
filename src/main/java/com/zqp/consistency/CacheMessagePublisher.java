package com.zqp.consistency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 缓存一致性消息发布器
 *
 * 当本实例发生数据变更时，通过 Redis Pub/Sub 广播缓存清除指令，
 * 通知集群中所有其他实例同步清空本地 Caffeine 缓存。
 *
 * 为什么用 Redis Pub/Sub：Redis 的发布订阅是即时的、轻量的消息通道，
 * 天然支持一对多广播，不需要引入 MQ。消息即发即忘，无持久化开销。
 */
@Component
public class CacheMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(CacheMessagePublisher.class);

    /** Redis Pub/Sub 频道名 */
    public static final String CHANNEL = "multi-cache:consistency";

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 发布缓存清除事件
     * @param cacheKey 需要清除的缓存 Key
     */
    public void publish(String cacheKey) {
        try {
            redisTemplate.convertAndSend(CHANNEL, cacheKey);
            log.debug("Published cache eviction: {}", cacheKey);
        } catch (Exception e) {
            // 发布失败不影响主流程：其他实例稍后通过 TTL 自然过期
            log.warn("Failed to publish cache eviction: {}", cacheKey, e);
        }
    }
}
