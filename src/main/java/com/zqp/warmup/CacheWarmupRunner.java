package com.zqp.warmup;

import com.zqp.bloom.BloomFilterService;
import com.zqp.config.MultiCacheProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 缓存预热执行器
 *
 * SpringBoot 启动完成后自动触发（ApplicationRunner），
 * 收集所有 CacheWarmupTask 实现类，在线程池中异步执行，
 * 不阻塞服务启动，首个请求即可享受缓存命中。
 *
 * 执行顺序：① 初始化布隆过滤器 → ② 并行执行所有预热任务
 */
@Component
@Order(1) // 在大多数 ApplicationRunner 之后执行
public class CacheWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CacheWarmupRunner.class);

    @Resource
    private BloomFilterService bloomFilter;

    @Resource
    private MultiCacheProperties properties;

    @Resource
    @Qualifier("cacheWarmupExecutor")
    private Executor warmupExecutor;

    /**
     * Spring 自动收集所有 CacheWarmupTask 实现 Bean
     * 如果没有业务方实现，列表为空，预热跳过
     */
    @Resource(required = false)
    private List<CacheWarmupTask> warmupTasks;

    @Override
    public void run(ApplicationArguments args) {
        if (warmupTasks == null || warmupTasks.isEmpty()) {
            log.info("No cache warmup tasks found, skip warmup");
            return;
        }

        // 第一步：初始化布隆过滤器（同步，必须先行）
        log.info("Initializing bloom filter...");
        bloomFilter.init();

        // 第二步：异步并行执行所有预热任务
        log.info("Starting cache warmup: {} tasks", warmupTasks.size());
        CountDownLatch latch = new CountDownLatch(warmupTasks.size());

        for (CacheWarmupTask task : warmupTasks) {
            warmupExecutor.execute(() -> {
                try {
                    long start = System.currentTimeMillis();
                    task.warmup();
                    long elapsed = System.currentTimeMillis() - start;
                    log.info("Warmup [{}] completed in {}ms", task.name(), elapsed);
                } catch (Exception e) {
                    log.error("Warmup [{}] failed", task.name(), e);
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有预热任务完成（超时时间从配置读取）
        int timeout = properties.getWarmup().getTimeoutSeconds();
        try {
            latch.await(timeout, TimeUnit.SECONDS);
            log.info("Cache warmup phase finished");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Cache warmup interrupted");
        }
    }
}
