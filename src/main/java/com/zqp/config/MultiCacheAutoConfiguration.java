package com.zqp.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zqp.consistency.CacheMessagePublisher;
import com.zqp.consistency.LocalCacheEvictHandler;
import org.redisson.Redisson;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.TimeUnit;

/**
 * 多级缓存组件自动配置类
 * 通过 spring.factories 或 @EnableMultiCache 激活
 * 所有可调参数通过 application.yml 的 multi-cache.* 前缀覆盖
 */
@Configuration
@EnableConfigurationProperties(MultiCacheProperties.class)
public class MultiCacheAutoConfiguration {

    // ==================== Caffeine 本地一级缓存 ====================

    @Bean
    @ConditionalOnMissingBean(name = "caffeineCache")
    public Cache<String, Object> caffeineCache(MultiCacheProperties properties) {
        MultiCacheProperties.Caffeine cfg = properties.getCaffeine();
        return Caffeine.newBuilder()
                .initialCapacity(cfg.getInitialCapacity())
                .maximumSize(cfg.getMaximumSize())
                .expireAfterWrite(cfg.getExpireAfterWriteMinutes(), TimeUnit.MINUTES)
                .expireAfterAccess(cfg.getExpireAfterAccessMinutes(), TimeUnit.MINUTES)
                .recordStats()
                .build();
    }

    // ==================== RedisTemplate 序列化配置 ====================

    @Bean
    @ConditionalOnMissingBean(name = "redisTemplate")
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    // ==================== Redisson 分布式锁客户端 ====================

    @Bean
    @ConditionalOnClass(Redisson.class)
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(name = "multi-cache.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedissonClient redissonClient(
            @Value("${spring.redis.host:localhost}") String host,
            @Value("${spring.redis.port:6379}") int port,
            @Value("${spring.redis.password:}") String password) {
        Config config = new Config();
        String address = "redis://" + host + ":" + port;
        config.useSingleServer().setAddress(address);
        if (password != null && !password.isEmpty()) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }

    // ==================== 布隆过滤器 — 穿透防护 ====================

    @Bean
    @ConditionalOnClass(Redisson.class)
    @ConditionalOnMissingBean(RBloomFilter.class)
    @ConditionalOnProperty(name = "multi-cache.bloom.enabled", havingValue = "true", matchIfMissing = true)
    public RBloomFilter<String> cacheBloomFilter(RedissonClient redissonClient) {
        return redissonClient.getBloomFilter("multi-cache:bloom");
    }

    // ==================== 缓存一致性 — Redis Pub/Sub ====================

    @Bean
    @ConditionalOnProperty(name = "multi-cache.redis.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "cacheConsistencyContainer")
    public RedisMessageListenerContainer cacheConsistencyContainer(
            RedisConnectionFactory connectionFactory,
            LocalCacheEvictHandler localCacheEvictHandler) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(localCacheEvictHandler,
                new ChannelTopic(CacheMessagePublisher.CHANNEL));
        return container;
    }

    // ==================== 缓存预热 — 异步线程池 ====================

    @Bean("cacheWarmupExecutor")
    @ConditionalOnMissingBean(name = "cacheWarmupExecutor")
    public ThreadPoolTaskExecutor cacheWarmupExecutor(MultiCacheProperties properties) {
        MultiCacheProperties.Warmup cfg = properties.getWarmup();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setThreadNamePrefix("cache-warmup-");
        executor.setRejectedExecutionHandler(
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
