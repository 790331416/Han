package com.han.common.core.util;

/**
 * ID生成工具类
 *
 * @deprecated 与 {@link HanIdUtil} 完全重复，已全部委托给 {@code HanIdUtil}。
 * 新代码请直接使用 {@link HanIdUtil}，存量调用点将在统一整改批次中迁移。
 */
@Deprecated(since = "1.0.0")
public final class XuIdUtil {

    private XuIdUtil() {}

    /**
     * 生成UUID
     */
    public static String uuid() {
        return HanIdUtil.uuid();
    }

    /**
     * 生成UUID（带分隔符）
     */
    public static String uuidWithHyphen() {
        return HanIdUtil.uuidWithHyphen();
    }

    /**
     * 生成数字ID
     */
    public static long nextId() {
        return HanIdUtil.nextId();
    }

    /**
     * 生成数字ID（指定范围）
     */
    public static long nextId(long origin, long bound) {
        return HanIdUtil.nextId(origin, bound);
    }

    /**
     * 生成雪花ID
     */
    public static long snowflakeId() {
        return HanIdUtil.snowflakeId();
    }

    /**
     * 生成雪花ID（指定 workerId 和 datacenterId）
     */
    public static long snowflakeId(long workerId, long datacenterId) {
        return HanIdUtil.snowflakeId(workerId, datacenterId);
    }
}
