package com.han.common.tenant.observe;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 无租户上下文观测器。
 *
 * <p>用于在不改变任何运行行为的前提下，把「这条 SQL 没有租户上下文」的事实记录下来：
 * 按「操作类型 + 表名 + 调用点」聚合计数，并按最小间隔输出告警日志，
 * 让运维能统计到底有多少条这样的访问、分别来自哪里。</p>
 *
 * <p>这是把 fail-open 翻成 fail-close 的前置证据链：静态走读给出的是下界，
 * 观测数据给出的才是实际值。</p>
 */
@Slf4j
public class MissingTenantContextRecorder {

    /** 解析调用点时要跳过的框架内部包，避免每条记录都指向本类自己 */
    private static final Set<String> INTERNAL_PACKAGE_PREFIXES = Set.of(
            "com.han.common.tenant.",
            "com.han.common.mybatis."
    );

    private static final String APPLICATION_PACKAGE_PREFIX = "com.han.";

    private static final String UNKNOWN_CALL_SITE = "unknown";

    private final Map<String, Occurrence> occurrences = new ConcurrentHashMap<>();

    private final boolean enabled;

    private final long logIntervalMillis;

    public MissingTenantContextRecorder() {
        this(true, 60_000L);
    }

    public MissingTenantContextRecorder(boolean enabled, long logIntervalMillis) {
        this.enabled = enabled;
        this.logIntervalMillis = Math.max(0L, logIntervalMillis);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 记录一次无租户上下文的数据访问。
     *
     * @param operation 操作类型，例如 SQL / INSERT
     * @param tableName 目标表名，取不到时可传实体名
     */
    public void record(String operation, String tableName) {
        if (!enabled) {
            return;
        }
        String op = operation == null ? "UNKNOWN" : operation;
        String table = tableName == null ? "UNKNOWN" : tableName;
        String callSite = resolveCallSite();
        String key = op + '|' + table + '|' + callSite;

        Occurrence occurrence = occurrences.computeIfAbsent(key, k -> new Occurrence(op, table, callSite));
        long total = occurrence.count.incrementAndGet();

        if (occurrence.shouldLog(logIntervalMillis)) {
            log.warn("无租户上下文的数据访问: operation={}, table={}, callSite={}, thread={}, 累计={}",
                    op, table, callSite, Thread.currentThread().getName(), total);
        }
    }

    /**
     * 当前已聚合的观测样本快照，供运维接口或测试读取。
     */
    public List<MissingTenantContextSample> snapshot() {
        return occurrences.values().stream()
                .map(item -> new MissingTenantContextSample(item.operation, item.tableName, item.callSite, item.count.get()))
                .sorted((left, right) -> Long.compare(right.count(), left.count()))
                .toList();
    }

    /**
     * 观测到的无租户上下文访问总次数。
     */
    public long totalCount() {
        return occurrences.values().stream().mapToLong(item -> item.count.get()).sum();
    }

    /**
     * 清空观测数据，仅用于测试与人工重新计数。
     */
    public void reset() {
        occurrences.clear();
    }

    /**
     * 取调用栈中第一个业务代码帧，作为「这条查询来自哪里」的定位信息。
     */
    private String resolveCallSite() {
        Optional<String> frame = StackWalker.getInstance().walk(frames -> frames
                .filter(item -> item.getClassName().startsWith(APPLICATION_PACKAGE_PREFIX))
                .filter(item -> INTERNAL_PACKAGE_PREFIXES.stream().noneMatch(prefix -> item.getClassName().startsWith(prefix)))
                .findFirst()
                .map(item -> item.getClassName() + '#' + item.getMethodName() + ':' + item.getLineNumber()));
        return frame.orElse(UNKNOWN_CALL_SITE);
    }

    private static final class Occurrence {

        private final String operation;
        private final String tableName;
        private final String callSite;
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong lastLogAt = new AtomicLong(0L);

        private Occurrence(String operation, String tableName, String callSite) {
            this.operation = operation;
            this.tableName = tableName;
            this.callSite = callSite;
        }

        private boolean shouldLog(long intervalMillis) {
            long now = System.currentTimeMillis();
            long last = lastLogAt.get();
            if (last != 0L && now - last < intervalMillis) {
                return false;
            }
            return lastLogAt.compareAndSet(last, now);
        }
    }
}
