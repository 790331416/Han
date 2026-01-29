package com.xuman.job.handler;

import com.xuman.job.shard.AverageShardStrategy;
import com.xuman.job.shard.ShardExecutor;
import com.xuman.job.shard.ShardRange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 示例分片任务 Handler
 * 展示如何使用 JobFlow 的分片能力
 */
@Slf4j
@Component("sampleShardTask")
@RequiredArgsConstructor
public class SampleShardTaskHandler {

    private final AverageShardStrategy shardStrategy;
    private final ShardExecutor shardExecutor;

    /**
     * 数据同步任务（分片执行）
     * 调用目标: sampleShardTask.syncData(1000000,10)
     * 参数: 总数据量,分片数
     */
    public void syncData(String params) {
        log.info("开始执行分片数据同步任务，参数: {}", params);

        // 解析参数: "总数据量,分片数"
        String[] parts = params.split(",");
        long totalCount = Long.parseLong(parts[0].trim());
        int shardTotal = Integer.parseInt(parts[1].trim());

        // 计算分片范围（这里用 jobId=1 作为示例）
        Long jobId = 1L;
        List<ShardRange> shardRanges = shardStrategy.split(jobId, totalCount, shardTotal);

        log.info("分片计算完成，共 {} 个分片", shardRanges.size());

        // 并行执行所有分片
        shardRanges.parallelStream().forEach(shardRange -> {
            shardExecutor.executeWithLock(shardRange, this::processShard, 300); // 5分钟超时
        });

        log.info("所有分片执行完成");
    }

    /**
     * 处理单个分片的业务逻辑
     */
    private void processShard(ShardRange shardRange) {
        log.info("处理分片: {}", shardRange);

        // 模拟查询数据库
        // SELECT * FROM orders WHERE id >= #{startId} AND id <= #{endId}
        
        long startId = shardRange.getStartId();
        long endId = shardRange.getEndId();

        // 模拟处理数据
        for (long id = startId; id <= endId; id++) {
            // 处理单条数据
            if (id % 10000 == 0) {
                log.info("分片 {}/{} 进度: 已处理 {} 条",
                        shardRange.getShardIndex(),
                        shardRange.getShardTotal(),
                        id - startId + 1);
            }
        }

        log.info("分片 {}/{} 处理完成，共处理 {} 条数据",
                shardRange.getShardIndex(),
                shardRange.getShardTotal(),
                shardRange.getDataCount());
    }

    /**
     * 数据清理任务（分片执行）
     * 调用目标: sampleShardTask.cleanExpiredData(500000,5)
     */
    public void cleanExpiredData(String params) {
        log.info("开始执行分片数据清理任务，参数: {}", params);

        String[] parts = params.split(",");
        long totalCount = Long.parseLong(parts[0].trim());
        int shardTotal = Integer.parseInt(parts[1].trim());

        Long jobId = 2L;
        List<ShardRange> shardRanges = shardStrategy.split(jobId, totalCount, shardTotal);

        // 串行执行清理任务（避免数据库压力过大）
        shardRanges.forEach(shardRange -> {
            shardExecutor.executeWithLock(shardRange, range -> {
                log.info("清理分片 {}/{} 的过期数据", range.getShardIndex(), range.getShardTotal());
                // DELETE FROM expired_data WHERE id >= #{startId} AND id <= #{endId}
                log.info("分片 {}/{} 清理完成", range.getShardIndex(), range.getShardTotal());
            });
        });

        log.info("所有分片清理完成");
    }
}
