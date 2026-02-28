package com.han.job.shard;

import java.util.List;

/**
 * 分片策略接口
 * JobFlow 核心特性：可扩展的分片策略
 */
public interface ShardStrategy {

    /**
     * 计算分片范围
     *
     * @param jobId 任务ID
     * @param totalCount 总数据量
     * @param shardTotal 分片总数
     * @return 分片范围列表
     */
    List<ShardRange> split(Long jobId, long totalCount, int shardTotal);
}
