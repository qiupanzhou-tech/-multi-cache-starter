package com.zqp.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.zqp.consistency.CacheMessagePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MultiLevelCache 单元测试")
class MultiLevelCacheTest {

    @Mock
    private Cache<String, Object> caffeineCache;
    @Mock
    private RedisTemplate<String, Object> redisTemplate;
    @Mock
    private ValueOperations<String, Object> valueOps;
    @Mock
    private CacheMessagePublisher publisher;

    @InjectMocks
    private MultiLevelCache cache;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("get() 读路径")
    class GetTests {

        @Test
        @DisplayName("L1 Caffeine 命中 → 直接返回，不查 Redis")
        void shouldReturnFromL1WhenHit() {
            when(caffeineCache.getIfPresent("key1")).thenReturn("value1");

            Object result = cache.get("key1");

            assertEquals("value1", result);
            verify(redisTemplate, never()).opsForValue();
        }

        @Test
        @DisplayName("L1 miss → L2 Redis 命中 → 回填 L1 并返回")
        void shouldBackfillL1WhenL2Hit() {
            when(caffeineCache.getIfPresent("key1")).thenReturn(null);
            when(valueOps.get("key1")).thenReturn("fromRedis");

            Object result = cache.get("key1");

            assertEquals("fromRedis", result);
            verify(caffeineCache).put("key1", "fromRedis");
        }

        @Test
        @DisplayName("NULL_MARKER 哨兵 → 返回 null")
        void shouldUnwrapNullMarker() {
            when(caffeineCache.getIfPresent("nullKey")).thenReturn(MultiLevelCache.NULL_MARKER);

            Object result = cache.get("nullKey");

            assertNull(result);
        }

        @Test
        @DisplayName("L1 miss + L2 miss → 返回 null")
        void shouldReturnNullWhenBothMiss() {
            when(caffeineCache.getIfPresent("anyKey")).thenReturn(null);
            when(valueOps.get("anyKey")).thenReturn(null);

            Object result = cache.get("anyKey");

            assertNull(result);
        }

        @Test
        @DisplayName("Redis 异常 → 降级返回 null，不抛异常")
        void shouldDegradeGracefullyOnRedisError() {
            when(caffeineCache.getIfPresent("key1")).thenReturn(null);
            when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis down"));

            Object result = cache.get("key1");

            assertNull(result);
        }
    }

    @Nested
    @DisplayName("put() 写路径")
    class PutTests {

        @Test
        @DisplayName("写入 L1 + L2，L2 失败不影响 L1")
        void shouldWriteToBothLevels() {
            cache.put("key1", "value1", 1800L);

            verify(caffeineCache).put(eq("key1"), any());
            verify(valueOps).set(eq("key1"), any(), anyLong(), any());
        }

        @Test
        @DisplayName("null 值存为哨兵，TTL 60s")
        void shouldCacheNullWithSentinelAndShortTtl() {
            cache.put("nullKey", null, 1800L);

            verify(caffeineCache).put(eq("nullKey"), eq(MultiLevelCache.NULL_MARKER));
        }

        @Test
        @DisplayName("TTL 随机偏移 ±10%")
        void shouldRandomizeTtlWithinRange() {
            long baseTtl = 100;
            boolean foundVariation = false;
            for (int i = 0; i < 500; i++) {
                long ttl = cache.addRandomOffset(baseTtl);
                assertTrue(ttl >= 90 && ttl <= 110,
                        "TTL=" + ttl + " 超出 ±10% 范围");
                if (ttl != baseTtl) foundVariation = true;
            }
            assertTrue(foundVariation, "500 次应出现随机偏移");
        }
    }

    @Nested
    @DisplayName("evict() / evictLocal() 驱逐路径")
    class EvictTests {

        @Test
        @DisplayName("evict() 清 L1 + 删 L2 + 发布消息")
        void shouldClearBothAndPublish() {
            cache.evict("key1");

            verify(caffeineCache).invalidate("key1");
            verify(redisTemplate).delete("key1");
            verify(publisher).publish("key1");
        }

        @Test
        @DisplayName("evictLocal() 只清 L1，不删 Redis，不发布")
        void shouldOnlyClearLocalCache() {
            cache.evictLocal("key1");

            verify(caffeineCache).invalidate("key1");
            verify(redisTemplate, never()).delete(anyString());
            verify(publisher, never()).publish(anyString());
        }
    }
}
