package com.han.sample.caller;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.han.common.tenant.observe.MissingTenantContextRecorder;

/**
 * 模拟业务侧调用方。
 *
 * <p>刻意放在 {@code com.han.common.mybatis} / {@code com.han.common.tenant} 之外的包，
 * 用来验证观测器解析出来的调用点是业务代码而不是框架内部帧。</p>
 */
public final class SampleBusinessCaller {

    private SampleBusinessCaller() {
    }

    public static final String QUERY_METHOD = "queryTenantScopedTable";

    public static final String RECORD_METHOD = "recordDirectly";

    public static boolean queryTenantScopedTable(TenantLineHandler handler, String tableName) {
        return handler.ignoreTable(tableName);
    }

    public static void recordDirectly(MissingTenantContextRecorder recorder, String operation, String tableName) {
        recorder.record(operation, tableName);
    }
}
