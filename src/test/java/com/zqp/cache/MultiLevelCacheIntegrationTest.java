package com.zqp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 集成测试：需要本地 Redis 运行。
 * 测试真实 Caffeine + Redis 两级缓存交互。
 * 运行方式：mvn test -Dgroups="integration" -DfailIfNoTests=false
 */
@SpringBootTest
@Tag("integration")
@DisplayName("MultiLevelCache 集成测试（需要 Redis）")
class MultiLevelCacheIntegrationTest {

    @Autowired
    private MultiLevelCache cache;

    @Autowired
    private Cache<String, Object> caffeineCache;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String TEST_KEY = "test:integration:key";
    private static final String TEST_VALUE = "integration-test-value";

    @BeforeEach
    void cleanUp() {
        caffeineCache.invalidate(TEST_KEY);
        redisTemplate.delete(TEST_KEY);
    }

    @Test
    @DisplayName("首次 get → L1/L2 miss → 返回 null")
    void shouldReturnNullOnFirstGet() {
        Object result = cache.get(TEST_KEY);
        assertNull(result, "首次查询应为 null");
    }

    @Test
    @DisplayName("put 后 get → L1 直接命中")
    void shouldHitL1AfterPut() {
        cache.put(TEST_KEY, TEST_VALUE, 60L);

        // 清除 Redis 只留 Caffeine，验证 L1 命中
        redisTemplate.delete(TEST_KEY);

        Object result = cache.get(TEST_KEY);
        assertEquals(TEST_VALUE, result, "L1 应命中");
    }

    @Test
    @DisplayName("仅存 L2 时 → L1 miss → L2 命中 → 回填 L1")
    void shouldBackfillL1FromL2() {
        // 直接写 Redis（模拟另一实例写入）
        redisTemplate.opsForValue().set(TEST_KEY, TEST_VALUE);

        // 第一次 get：L1 miss → L2 hit → 回填 L1
        Object result1 = cache.get(TEST_KEY);
        assertEquals(TEST_VALUE, result1);

        // 删除 Redis，第二次 get 应命中 L1（已回填）
        redisTemplate.delete(TEST_KEY);
        Object result2 = cache.get(TEST_KEY);
        assertEquals(TEST_VALUE, result2, "L2 命中后应回填 L1");
    }

    @Test
    @DisplayName("put null → 缓存 NULL_MARKER 哨兵 → get 返回 null")
    void shouldCacheNullAsSentinel() {
        cache.put(TEST_KEY, null, 60L);

        Object result = cache.get(TEST_KEY);
        assertNull(result, "null 值缓存应返回 null");
        // L1 中应该是 NULL_MARKER
        Object l1Raw = caffeineCache.getIfPresent(TEST_KEY);
        assertEquals("__CACHE_NULL__", l1Raw, "L1 应存有哨兵值");
    }

    @Test
    @DisplayName("evict() 同时清 L1 和 L2")
    void shouldEvictBothLevels() {
        cache.put(TEST_KEY, TEST_VALUE, 60L);
        assertNotNull(cache.get(TEST_KEY));

        cache.evict(TEST_KEY);

        assertNull(cache.get(TEST_KEY), "驱逐后 L1+L2 都应为空");
        assertNull(caffeineCache.getIfPresent(TEST_KEY));
        assertNull(redisTemplate.opsForValue().get(TEST_KEY));
    }

    @Test
    @DisplayName("evictLocal() 只清 L1，保留 L2")
    void shouldEvictOnlyLocal() {
        // 写入两级
        cache.put(TEST_KEY, TEST_VALUE, 60L);

        cache.evictLocal(TEST_KEY);

        assertNull(caffeineCache.getIfPresent(TEST_KEY), "L1 应被清空");
        assertNotNull(redisTemplate.opsForValue().get(TEST_KEY), "L2 应保留");
    }

    @Test
    @DisplayName("TTL 随机偏移在 ±10% 范围内")
    void shouldRandomizeTtlWithinRange() {
        long baseTtl = 100;
        boolean foundVariation = false;
        for (int i = 0; i < 200; i++) {
            long ttl = cache.addRandomOffset(baseTtl);
            assertTrue(ttl >= 90 && ttl <= 110, "TTL=" + ttl + " 超出 ±10% 范围");
            if (ttl != baseTtl) foundVariation = true;
        }
        assertTrue(foundVariation, "200 次采样应出现随机偏移");
    }
}
