package com.han.common.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HanIdUtil} 单元测试，覆盖工单 S-72 的雪花 ID 重复问题。
 */
class HanIdUtilTest {

    /** workerId / datacenterId 各占 5 位，取值上限 */
    private static final long MAX_NODE_ID = 31L;

    /** 与生成器保持一致的位布局，用于反解 ID 校验格式未变 */
    private static final long TWEPOCH = 1288834974657L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    @Test
    @DisplayName("workerId / datacenterId 必须落在 5bit 范围内")
    void nodeIdsWithinRange() {
        assertTrue(HanIdUtil.getWorkerId() >= 0 && HanIdUtil.getWorkerId() <= MAX_NODE_ID);
        assertTrue(HanIdUtil.getDatacenterId() >= 0 && HanIdUtil.getDatacenterId() <= MAX_NODE_ID);
    }

    @Test
    @DisplayName("ID 位布局不变：反解出的 workerId / datacenterId 与实际配置一致")
    void idLayoutUnchanged() {
        long id = HanIdUtil.snowflakeId();

        long sequenceMask = -1L ^ (-1L << SEQUENCE_BITS);
        long workerMask = -1L ^ (-1L << WORKER_ID_BITS);
        long datacenterMask = -1L ^ (-1L << DATACENTER_ID_BITS);

        long worker = (id >> SEQUENCE_BITS) & workerMask;
        long datacenter = (id >> (SEQUENCE_BITS + WORKER_ID_BITS)) & datacenterMask;
        long timestamp = (id >>> TIMESTAMP_SHIFT) + TWEPOCH;

        assertEquals(HanIdUtil.getWorkerId(), worker);
        assertEquals(HanIdUtil.getDatacenterId(), datacenter);
        assertTrue((id & sequenceMask) >= 0);
        // 时间戳段应当落在「不早于本次测试启动前一分钟」的合理区间
        assertTrue(Math.abs(System.currentTimeMillis() - timestamp) < 60_000L,
                "反解时间戳偏差过大: " + timestamp);
    }

    @Test
    @DisplayName("HanIdUtil 与 XuIdUtil 共用同一个生成器，不再产生进程内重复 ID")
    void hanAndXuShareOneGenerator() {
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertTrue(ids.add(HanIdUtil.snowflakeId()), "HanIdUtil 生成了重复 ID");
            assertTrue(ids.add(XuIdUtil.snowflakeId()), "XuIdUtil 与 HanIdUtil 生成了重复 ID");
        }
        assertEquals(4000, ids.size());
    }

    @Test
    @DisplayName("并发生成不产生重复 ID")
    void concurrentGenerationIsUnique() throws Exception {
        int threads = 8;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<Long>>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    List<Long> generated = new ArrayList<>(perThread);
                    for (int i = 0; i < perThread; i++) {
                        generated.add(HanIdUtil.snowflakeId());
                    }
                    return generated;
                }));
            }
            Set<Long> all = new HashSet<>();
            for (Future<List<Long>> future : futures) {
                all.addAll(future.get(30, TimeUnit.SECONDS));
            }
            assertEquals(threads * perThread, all.size(), "并发生成出现重复 ID");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("显式指定不同节点标识时生成的 ID 不同")
    void explicitNodeIdsProduceDifferentIds() {
        assertNotEquals(HanIdUtil.snowflakeId(3, 4), HanIdUtil.snowflakeId(5, 6));
    }
}
