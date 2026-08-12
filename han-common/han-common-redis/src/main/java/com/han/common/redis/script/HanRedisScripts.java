package com.han.common.redis.script;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

/**
 * 公共 Lua 脚本常量。
 * <p>
 * 「写入」与「设置过期」拆成两次往返，中间一旦进程被 kill、Redis 主从切换或连接闪断，
 * 就会留下一个<b>永不过期</b>的 key。这个坑在本仓库出现过两次：
 * 登录失败计数（账号被永久锁死，用户按提示等 N 分钟也不会解锁）、
 * SSO ticket（留下一张永久有效的身份凭证）。
 * <p>
 * 网关侧的限流实现已经用 Lua 修好了同一个坑
 * （{@code han-gateway/.../RateLimitFilter.java} 的 {@code RATE_LIMIT_SCRIPT}），
 * 这里把同一套写法提到公共层，供各服务复用。
 */
public final class HanRedisScripts {

    private HanRedisScripts() {}

    /**
     * 原子计数：INCR 之后在同一脚本内保证 key 一定带 TTL。
     * <p>TTL 判断用 {@code < 0} 兜底存量的永生 key（TTL 返回 -1 表示无过期时间）。
     * <p>KEYS[1] = 计数 key，ARGV[1] = 过期秒数，返回自增后的值。
     */
    public static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 or redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "end "
                    + "return current",
            Long.class);

    /**
     * 原子写 hash：一次性写入若干 field 并设置 TTL。
     * <p>KEYS[1] = hash key，ARGV[1] = 过期秒数，ARGV[2..] = field/value 交替排列，返回写入的 field 数。
     */
    public static final RedisScript<Long> HASH_SET_WITH_TTL = new DefaultRedisScript<>(
            "local count = 0 "
                    + "for i = 2, #ARGV, 2 do "
                    + "redis.call('HSET', KEYS[1], ARGV[i], ARGV[i + 1]) "
                    + "count = count + 1 "
                    + "end "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "return count",
            Long.class);

    /**
     * 一次性读取并删除（一次性凭证的正确消费方式，避免「校验通过后再删」之间的重放窗口）。
     * <p>KEYS[1] = key，返回原值或 nil。
     */
    public static final RedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            "local value = redis.call('GET', KEYS[1]) "
                    + "if value then redis.call('DEL', KEYS[1]) end "
                    + "return value",
            String.class);

    /**
     * 一次性读取并删除 hash。
     * <p>KEYS[1] = hash key，返回 field/value 交替排列的数组（key 不存在时为空数组）。
     */
    @SuppressWarnings("rawtypes")
    public static final RedisScript<List> HASH_GET_ALL_AND_DELETE = new DefaultRedisScript<>(
            "local values = redis.call('HGETALL', KEYS[1]) "
                    + "if #values > 0 then redis.call('DEL', KEYS[1]) end "
                    + "return values",
            List.class);

    /**
     * 兜底修复：key 存在但没有 TTL 时补设过期时间，用于清理历史遗留的永生 key。
     * <p>KEYS[1] = key，ARGV[1] = 过期秒数，返回 1 表示补设成功、0 表示无需处理。
     */
    public static final RedisScript<Long> ENSURE_TTL = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 and redis.call('TTL', KEYS[1]) < 0 then "
                    + "redis.call('EXPIRE', KEYS[1], tonumber(ARGV[1])) "
                    + "return 1 "
                    + "end "
                    + "return 0",
            Long.class);
}
