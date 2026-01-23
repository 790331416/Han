package com.xuman.common.core.util;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.lang.Snowflake;

/**
 * ID生成工具类（封装Hutool）
 */
public final class XuIdUtil {

    private static final Snowflake SNOWFLAKE = IdUtil.getSnowflake();

    private XuIdUtil() {}

    /**
     * 雪花算法ID
     */
    public static long snowflakeId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 雪花算法ID（字符串）
     */
    public static String snowflakeIdStr() {
        return SNOWFLAKE.nextIdStr();
    }

    /**
     * UUID（无横线）
     */
    public static String simpleUUID() {
        return IdUtil.simpleUUID();
    }

    /**
     * UUID
     */
    public static String uuid() {
        return IdUtil.randomUUID();
    }

    /**
     * NanoId（更短的唯一ID）
     */
    public static String nanoId() {
        return IdUtil.nanoId();
    }

    /**
     * NanoId（指定长度）
     */
    public static String nanoId(int size) {
        return IdUtil.nanoId(size);
    }

    /**
     * ObjectId
     */
    public static String objectId() {
        return IdUtil.objectId();
    }
}
