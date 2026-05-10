package com.zqp.bloom;

import com.zqp.config.MultiCacheProperties;
import org.redisson.api.RBloomFilter;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 布隆过滤器服务 — 缓存穿透第一道防线
 *
 * 原理：布隆过滤器是一种概率型数据结构，用多个哈希函数将元素映射到位数组中。
 * - 返回 false：元素一定不存在 → 直接拒绝，不查缓存、不查 DB
 * - 返回 true：元素可能存在（有误判率）→ 继续正常缓存查询流程
 *
 * 为什么用 Redisson 实现：位数组存在 Redis，所有实例共享同一过滤器。
 */
@Component
public class BloomFilterService {

    @Resource
    private RBloomFilter<String> cacheBloomFilter;

    @Resource
    private MultiCacheProperties properties;

    /** 是否已初始化 */
    private volatile boolean initialized = false;

    /**
     * 初始化布隆过滤器（幂等，多次调用不重复初始化）
     * 由预热模块在启动时调用
     */
    public synchronized void init() {
        if (!initialized) {
            MultiCacheProperties.Bloom cfg = properties.getBloom();
            cacheBloomFilter.tryInit(cfg.getExpectedInsertions(), cfg.getFalseProbability());
            initialized = true;
        }
    }

    /**
     * 判断 Key 是否可能存在
     * @return false = 一定不存在（直接拒绝）；true = 可能存在（继续查缓存）
     */
    public boolean mightContain(String key) {
        if (!initialized) {
            return true; // 未初始化时放行
        }
        return cacheBloomFilter.contains(key);
    }

    /**
     * 添加 Key 到布隆过滤器（数据写入 DB 成功后调用）
     */
    public void add(String key) {
        if (!initialized) {
            init();
        }
        cacheBloomFilter.add(key);
    }

    public boolean isInitialized() {
        return initialized;
    }
}
