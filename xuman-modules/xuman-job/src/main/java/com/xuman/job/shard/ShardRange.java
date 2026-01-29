package com.xuman.job.shard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分片范围
 * JobFlow 核心特性：明确的分片范围
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShardRange {

    /**
     * 分片索引（从0开始）
     */
    private int shardIndex;

    /**
     * 总分片数
     */
    private int shardTotal;

    /**
     * 起始ID（包含）
     */
    private long startId;

    /**
     * 结束ID（包含）
     */
    private long endId;

    /**
     * 分布式锁的键
     */
    private String lockKey;

    /**
     * 获取分片的数据量
     */
    public long getDataCount() {
        return endId - startId + 1;
    }

    /**
     * 生成锁的键
     */
    public static String generateLockKey(Long jobId, int shardIndex) {
        return String.format("lock:job:%d:shard:%d", jobId, shardIndex);
    }

    @Override
    public String toString() {
        return String.format("Shard[%d/%d]: ID范围[%d-%d], 数据量=%d",
                shardIndex, shardTotal, startId, endId, getDataCount());
    }
}
