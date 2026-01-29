package com.xuman.job.context;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * TraceId 上下文管理
 * JobFlow 核心特性：全链路追踪
 */
public class TraceContext {

    private static final String TRACE_ID_KEY = "traceId";
    private static final String JOB_ID_KEY = "jobId";
    private static final String SHARD_INDEX_KEY = "shardIndex";

    /**
     * 生成新的 TraceId
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 设置 TraceId 到 MDC
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 获取当前 TraceId
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = generateTraceId();
            setTraceId(traceId);
        }
        return traceId;
    }

    /**
     * 设置任务ID到 MDC
     */
    public static void setJobId(Long jobId) {
        MDC.put(JOB_ID_KEY, String.valueOf(jobId));
    }

    /**
     * 设置分片索引到 MDC
     */
    public static void setShardIndex(Integer shardIndex) {
        if (shardIndex != null) {
            MDC.put(SHARD_INDEX_KEY, String.valueOf(shardIndex));
        }
    }

    /**
     * 清除所有 MDC 上下文
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * 获取完整的上下文信息（用于日志）
     */
    public static String getContextInfo() {
        return String.format("traceId=%s, jobId=%s, shardIndex=%s",
                MDC.get(TRACE_ID_KEY),
                MDC.get(JOB_ID_KEY),
                MDC.get(SHARD_INDEX_KEY));
    }
}
