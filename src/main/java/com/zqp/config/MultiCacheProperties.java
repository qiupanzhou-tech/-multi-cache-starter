package com.zqp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多级缓存防护组件配置属性
 * 所有可配置参数集中管理，支持 application.yml 覆盖
 *
 * 配置前缀: multi-cache
 * 示例:
 * multi-cache:
 *   caffeine:
 *     maximum-size: 20000
 *     expire-after-write-minutes: 60
 *   bloom:
 *     expected-insertions: 200000
 *     false-probability: 0.02
 */
@ConfigurationProperties(prefix = "multi-cache")
public class MultiCacheProperties {

    /** Caffeine 本地缓存配置 */
    private Caffeine caffeine = new Caffeine();

    /** Redis 二级缓存配置 */
    private Redis redis = new Redis();

    /** 布隆过滤器配置 */
    private Bloom bloom = new Bloom();

    /** 缓存预热配置 */
    private Warmup warmup = new Warmup();

    // ==================== 内部类 ====================

    public static class Caffeine {
        private int initialCapacity = 100;
        private long maximumSize = 10000;
        private int expireAfterWriteMinutes = 30;
        private int expireAfterAccessMinutes = 5;

        public int getInitialCapacity() { return initialCapacity; }
        public void setInitialCapacity(int initialCapacity) { this.initialCapacity = initialCapacity; }
        public long getMaximumSize() { return maximumSize; }
        public void setMaximumSize(long maximumSize) { this.maximumSize = maximumSize; }
        public int getExpireAfterWriteMinutes() { return expireAfterWriteMinutes; }
        public void setExpireAfterWriteMinutes(int expireAfterWriteMinutes) { this.expireAfterWriteMinutes = expireAfterWriteMinutes; }
        public int getExpireAfterAccessMinutes() { return expireAfterAccessMinutes; }
        public void setExpireAfterAccessMinutes(int expireAfterAccessMinutes) { this.expireAfterAccessMinutes = expireAfterAccessMinutes; }
    }

    public static class Redis {
        /** 是否启用 Redis 二级缓存（关闭后只使用 L1 Caffeine） */
        private boolean enabled = true;
        /** Redis Key 前缀 */
        private String keyPrefix = "multi-cache";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    }

    public static class Bloom {
        /** 是否启用布隆过滤器 */
        private boolean enabled = true;
        /** 期望插入量 */
        private long expectedInsertions = 100000L;
        /** 误判率 */
        private double falseProbability = 0.01;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getExpectedInsertions() { return expectedInsertions; }
        public void setExpectedInsertions(long expectedInsertions) { this.expectedInsertions = expectedInsertions; }
        public double getFalseProbability() { return falseProbability; }
        public void setFalseProbability(double falseProbability) { this.falseProbability = falseProbability; }
    }

    public static class Warmup {
        /** 预热线程池核心大小 */
        private int corePoolSize = 2;
        /** 预热线程池最大大小 */
        private int maxPoolSize = 4;
        /** 预热总超时时间（秒） */
        private int timeoutSeconds = 120;

        public int getCorePoolSize() { return corePoolSize; }
        public void setCorePoolSize(int corePoolSize) { this.corePoolSize = corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    // ==================== getter/setter ====================

    public Caffeine getCaffeine() { return caffeine; }
    public void setCaffeine(Caffeine caffeine) { this.caffeine = caffeine; }
    public Redis getRedis() { return redis; }
    public void setRedis(Redis redis) { this.redis = redis; }
    public Bloom getBloom() { return bloom; }
    public void setBloom(Bloom bloom) { this.bloom = bloom; }
    public Warmup getWarmup() { return warmup; }
    public void setWarmup(Warmup warmup) { this.warmup = warmup; }
}
