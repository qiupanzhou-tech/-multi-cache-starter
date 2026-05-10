package com.zqp.warmup;

/**
 * 缓存预热任务接口
 *
 * 业务方实现此接口并在方法中定义预热逻辑（查询 DB、写入缓存）。
 * 所有实现类会被 CacheWarmupRunner 自动发现并异步执行。
 *
 * 使用示例:
 * @Component
 * public class UserCacheWarmupTask implements CacheWarmupTask {
 *     public void warmup() { ... }
 *     public String name() { return "用户缓存预热"; }
 * }
 */
public interface CacheWarmupTask {

    /**
     * 执行预热逻辑：查 DB → 写入多级缓存（L1 + L2）
     * 此方法在独立线程中异步执行，不阻塞应用启动
     */
    void warmup();

    /**
     * 预热任务名称（用于日志）
     */
    String name();
}
