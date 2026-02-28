package com.han.common.core.util;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ID生成工具类
 */
public final class XuIdUtil {

    private XuIdUtil() {}

    /**
     * 生成UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成UUID（带分隔符）
     */
    public static String uuidWithHyphen() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成数字ID
     */
    public static long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    /**
     * 生成数字ID（指定范围）
     */
    public static long nextId(long origin, long bound) {
        return ThreadLocalRandom.current().nextLong(origin, bound);
    }

    /** 默认雪花ID生成器（单例，线程安全） */
    private static final SnowflakeIdWorker DEFAULT_ID_WORKER = new SnowflakeIdWorker(1, 1);

    /**
     * 生成雪花ID（使用默认 workerId=1, datacenterId=1）
     */
    public static long snowflakeId() {
        return DEFAULT_ID_WORKER.nextId();
    }

    /**
     * 生成雪花ID（指定 workerId 和 datacenterId）
     * <p>注意：每组 (workerId, datacenterId) 应保持单例，频繁创建会导致ID重复</p>
     */
    public static long snowflakeId(long workerId, long datacenterId) {
        return new SnowflakeIdWorker(workerId, datacenterId).nextId();
    }

    /**
     * 雪花ID生成器
     */
    private static class SnowflakeIdWorker {
        private final long twepoch = 1288834974657L;
        private final long workerIdBits = 5L;
        private final long datacenterIdBits = 5L;
        private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
        private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
        private final long sequenceBits = 12L;
        private final long workerIdShift = sequenceBits;
        private final long datacenterIdShift = sequenceBits + workerIdBits;
        private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
        private final long sequenceMask = -1L ^ (-1L << sequenceBits);
        private long workerId;
        private long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SnowflakeIdWorker(long workerId, long datacenterId) {
            if (workerId > maxWorkerId || workerId < 0) {
                throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
            }
            if (datacenterId > maxDatacenterId || datacenterId < 0) {
                throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized long nextId() {
            long timestamp = timeGen();
            if (timestamp < lastTimestamp) {
                throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", lastTimestamp - timestamp));
            }
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & sequenceMask;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - twepoch) << timestampLeftShift) | (datacenterId << datacenterIdShift) | (workerId << workerIdShift) | sequence;
        }

        protected long timeGen() {
            return System.currentTimeMillis();
        }

        protected long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }
    }
}
