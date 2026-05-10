# 多级缓存高并发防护通用组件 (Multi-Level Cache Starter)

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.18-brightgreen)](https://spring.io/)
[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/)
[![License](https://img.shields.io/badge/license-MIT-green)](LICENSE)

基于 **Caffeine + Redis + 布隆过滤器** 的可插拔式多级缓存组件，通过 `@CacheProtect` / `@CacheEvict` 注解零侵入接入，一站式解决缓存穿透、击穿、雪崩三大问题。

---

## 架构设计

```
请求 → SpEL解析Key → 布隆过滤器(穿透) → Caffeine L1(纳秒级)
                                            ↓ miss
                                       Redis L2(毫秒级)
                                            ↓ miss
                                  Redisson分布式锁(击穿)
                                            ↓
                                  双重校验 → DB → 回写L2+L1
```

### 缓存一致性

```
实例A 更新数据 → evict(L1+L2) → Redis Pub/Sub 广播 → 实例B/C/D 清 L1
                                                    ↓
                                        evictLocal() 只清L1，防止无限循环
```

---

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.zqp</groupId>
    <artifactId>multi-cache-protect</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置 application.yml

```yaml
multi-cache:
  caffeine:
    maximum-size: 10000
    expire-after-write-minutes: 30
  redis:
    enabled: true
  bloom:
    enabled: true
    expected-insertions: 100000
    false-probability: 0.01
```

### 3. 在启动类启用

```java
@SpringBootApplication
@EnableMultiCache
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4. 在 Service 方法上加注解

```java
// 读操作 —— 自动走多级缓存
@Override
@CacheProtect(key = "user:info", keyExpression = "#id", ttl = 1800)
public User getUserById(Long id) {
    return userMapper.selectUserById(id);
}

// 写操作 —— 自动清缓存
@Override
@CacheEvict(keys = {"user:info:#id", "user:list"})
public int removeUser(Long id) {
    return userMapper.deleteUserById(id);
}
```

---

## 三层防护策略

| 问题 | 原因 | 本组件方案 |
|------|------|------------|
| **缓存穿透** | 查询不存在的数据，绕过缓存直击 DB | ① 布隆过滤器前置拦截非法 Key ② 空值缓存 60s 兜底 |
| **缓存击穿** | 热点 Key 过期瞬间大量请求打到 DB | Redisson 分布式锁 + 双重校验，仅一个线程查库 |
| **缓存雪崩** | 大量 Key 同时过期 | TTL ±10% 随机偏移，分散过期时间 |

---

## 组件特性

- **零侵入接入**：两个注解 `@CacheProtect` / `@CacheEvict`，业务代码无需改动
- **多级降级**：Redis 故障 → 自动降级为 Caffeine + DB，服务不中断
- **SpEL 动态 Key**：支持 `#id`、`#user.id` 等 SpEL 表达式动态构建缓存 Key
- **集群一致性**：Redis Pub/Sub 广播，写操作后通知所有实例清 L1 本地缓存
- **异步预热**：`ApplicationRunner` + 线程池，启动时预热热点数据
- **配置外部化**：所有参数通过 `application.yml` 覆盖，无硬编码

---

## 配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `multi-cache.caffeine.initial-capacity` | 100 | Caffeine 初始容量 |
| `multi-cache.caffeine.maximum-size` | 10000 | Caffeine 最大容量 |
| `multi-cache.caffeine.expire-after-write-minutes` | 30 | 写后过期时间（分钟） |
| `multi-cache.caffeine.expire-after-access-minutes` | 5 | 访问后过期时间（分钟） |
| `multi-cache.redis.enabled` | true | 是否启用 Redis L2 |
| `multi-cache.redis.key-prefix` | multi-cache | Redis Key 前缀 |
| `multi-cache.bloom.enabled` | true | 是否启用布隆过滤器 |
| `multi-cache.bloom.expected-insertions` | 100000 | 布隆过滤器预期插入量 |
| `multi-cache.bloom.false-probability` | 0.01 | 误判率（1%） |
| `multi-cache.warmup.core-pool-size` | 2 | 预热线程池核心大小 |

---

## 项目结构

```
src/main/java/com/zqp/
├── annotation/
│   ├── CacheProtect.java        # 缓存保护注解（读）
│   ├── CacheEvict.java          # 缓存驱逐注解（写）
│   └── EnableMultiCache.java    # 启用组件注解
├── aspect/
│   ├── CacheProtectAspect.java  # 核心 AOP 切面（7 步链路）
│   └── CacheEvictAspect.java    # 缓存驱逐切面
├── cache/
│   └── MultiLevelCache.java     # 多级缓存引擎（L1→L2→回填）
├── bloom/
│   └── BloomFilterService.java  # Redisson 布隆过滤器封装
├── config/
│   ├── MultiCacheAutoConfiguration.java  # SPI 自动装配
│   └── MultiCacheProperties.java         # @ConfigurationProperties
├── consistency/
│   ├── CacheMessagePublisher.java        # Pub/Sub 消息发布
│   └── LocalCacheEvictHandler.java       # Pub/Sub 消息订阅
├── warmup/
│   ├── CacheWarmupTask.java              # 预热任务接口
│   ├── CacheWarmupRunner.java            # ApplicationRunner 预热调度
│   └── UserCacheWarmupTask.java          # 示例预热任务
├── controller/UserController.java
├── service/UserService.java
├── service/impl/UserServiceImpl.java
├── mapper/UserMapper.java
├── entity/User.java
└── util/Result.java
```

---

## 环境要求

- JDK 17+
- Spring Boot 2.7+
- Redis（可选，可退化为纯 Caffeine 模式）
- MySQL（业务数据库）

---

## 启动

```bash
# 1. 确保 MySQL 和 Redis 已启动
# 2. 创建数据库 multi_cache 并导入表结构
# 3. 修改 application.yml 中数据库密码
# 4. 启动项目
mvn spring-boot:run
```
