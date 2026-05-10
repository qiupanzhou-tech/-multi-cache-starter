package com.zqp.consistency;

import com.zqp.cache.MultiLevelCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;

/**
 * 本地缓存清除消息处理器
 *
 * 监听 Redis 频道 "multi-cache:consistency"，收到消息后清除本实例的 Caffeine L1 缓存。
 * 只清 L1（Caffeine），不清 L2（Redis）——因为 L2 是共享的，发布方已经清过了。
 * 不清 L2 也避免了"收到消息 → 清 L2 → 又发布消息"的无限循环。
 */
@Component
public class LocalCacheEvictHandler implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(LocalCacheEvictHandler.class);

    @Resource
    private MultiLevelCache cache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String cacheKey = new String(message.getBody(), StandardCharsets.UTF_8);
        log.debug("Received cache eviction event: {}", cacheKey);
        cache.evictLocal(cacheKey);
    }
}
