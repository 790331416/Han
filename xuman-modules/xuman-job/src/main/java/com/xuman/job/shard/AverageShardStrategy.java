package com.xuman.job.shard;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 均匀分片策略（默认实现）
 * JobFlow 核心特性：智能分片
 */
@Slf4j
@Component
public class AverageShardStrategy implements ShardStrategy {

    @Override
    public List<ShardRange> split(Long jobId, long totalCount, int shardTotal) {
        if (totalCount <= 0) {
            log.warn("数据总量为0，无需分片");
            return new ArrayList<>();
        }

        if (shardTotal <= 0) {
            shardTotal = 1;
        }

        List<ShardRange> ranges = new ArrayList<>(shardTotal);

        // 计算每个分片的平均数据量
        long avgCount = totalCount / shardTotal;
        long remainder = totalCount % shardTotal;

        long currentStart = 1;

        for (int i = 0; i < shardTotal; i++) {
            // 将余数均匀分配到前面的分片中
            long count = avgCount + (i < remainder ? 1 : 0);
            long currentEnd = currentStart + count - 1;

            ShardRange range = new ShardRange();
            range.setShardIndex(i);
            range.setShardTotal(shardTotal);
            range.setStartId(currentStart);
            range.setEndId(currentEnd);
            range.setLockKey(ShardRange.generateLockKey(jobId, i));

            ranges.add(range);

            log.debug("分片 {}/{}: ID范围[{}-{}], 数据量={}",
                    i, shardTotal, currentStart, currentEnd, count);

            currentStart = currentEnd + 1;
        }

        log.info("分片计算完成: 总数据量={}, 分片数={}", totalCount, shardTotal);
        return ranges;
    }
}
