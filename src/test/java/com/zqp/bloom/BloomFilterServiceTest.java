package com.zqp.bloom;

import com.zqp.config.MultiCacheProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BloomFilterService 单元测试")
class BloomFilterServiceTest {

    @Mock
    private RBloomFilter<String> rBloomFilter;
    @Mock
    private MultiCacheProperties properties;
    @Mock
    private MultiCacheProperties.Bloom bloomConfig;

    @InjectMocks
    private BloomFilterService bloomFilterService;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getBloom()).thenReturn(bloomConfig);
        lenient().when(bloomConfig.getExpectedInsertions()).thenReturn(100000L);
        lenient().when(bloomConfig.getFalseProbability()).thenReturn(0.01);
    }

    @Nested
    @DisplayName("未初始化状态")
    class NotInitialized {

        @Test
        @DisplayName("未初始化时 mightContain 返回 true（放行不误拦）")
        void shouldReturnTrueWhenNotInitialized() {
            assertTrue(bloomFilterService.mightContain("any-key"));
        }

        @Test
        @DisplayName("未初始化时 add 先触发 init 再添加")
        void shouldInitThenAdd() {
            bloomFilterService.add("new-key");

            verify(rBloomFilter).tryInit(anyLong(), anyDouble());
            verify(rBloomFilter).add("new-key");
        }
    }

    @Nested
    @DisplayName("已初始化状态")
    class Initialized {

        @BeforeEach
        void initBloom() {
            bloomFilterService.init();
        }

        @Test
        @DisplayName("init 只执行一次（幂等）")
        void shouldInitOnlyOnce() {
            bloomFilterService.init(); // 第二次调用

            verify(rBloomFilter, times(1)).tryInit(anyLong(), anyDouble());
        }

        @Test
        @DisplayName("布隆返回 true → mightContain 返回 true")
        void shouldReturnTrueWhenBloomSaysMightExist() {
            when(rBloomFilter.contains("exist-key")).thenReturn(true);

            assertTrue(bloomFilterService.mightContain("exist-key"));
        }

        @Test
        @DisplayName("布隆返回 false → mightContain 返回 false（一定不存在）")
        void shouldReturnFalseWhenBloomSaysNotExist() {
            when(rBloomFilter.contains("no-such-key")).thenReturn(false);

            assertFalse(bloomFilterService.mightContain("no-such-key"));
        }
    }
}
