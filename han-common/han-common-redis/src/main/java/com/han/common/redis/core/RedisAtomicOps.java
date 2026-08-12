package com.han.common.redis.core;

import com.han.common.redis.script.HanRedisScripts;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 原子操作门面。
 * <p>
 * 收口「写入 + 设置 TTL」这类必须一次完成的组合操作。业务侧不要再自己写
 * {@code increment()} 后跟一行 {@code expire()} —— 两次往返之间断开就会留下永不过期的 key，
 * 后果分别是账号被永久锁定和身份凭证永久有效。
 *
 * @see HanRedisScripts
 */
public class RedisAtomicOps {

    private final StringRedisTemplate redisTemplate;

    public RedisAtomicOps(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 原子自增并保证 key 带 TTL。
     * <p>典型场景：登录失败计数、绑定失败计数、接口调用配额。
     *
     * @return 自增后的计数值
     */
    public long incrementWithTtl(String key, Duration ttl) {
        requireKey(key);
        Long value = redisTemplate.execute(HanRedisScripts.INCREMENT_WITH_TTL,
                List.of(key), String.valueOf(toSeconds(ttl)));
        return value != null ? value : 0L;
    }

    /**
     * 原子写入若干 hash field 并设置 TTL。
     * <p>典型场景：SSO ticket、一次性授权码这类带多字段的短时凭证。
     *
     * @return 实际写入的 field 数
     */
    public long hashSetWithTtl(String key, Map<String, String> fields, Duration ttl) {
        requireKey(key);
        if (fields == null || fields.isEmpty()) {
            return 0L;
        }
        List<String> args = new ArrayList<>(fields.size() * 2 + 1);
        args.add(String.valueOf(toSeconds(ttl)));
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            args.add(entry.getKey());
            args.add(entry.getValue() != null ? entry.getValue() : "");
        }
        Long count = redisTemplate.execute(HanRedisScripts.HASH_SET_WITH_TTL,
                List.of(key), args.toArray(new String[0]));
        return count != null ? count : 0L;
    }

    /**
     * 原子读取并删除，用于一次性凭证消费。
     *
     * @return 原值；key 不存在时返回 {@code null}
     */
    public String getAndDelete(String key) {
        requireKey(key);
        return redisTemplate.execute(HanRedisScripts.GET_AND_DELETE, List.of(key));
    }

    /**
     * 原子读取并删除 hash，用于一次性凭证消费。
     *
     * @return field/value 映射；key 不存在时返回空 Map
     */
    @SuppressWarnings("rawtypes")
    public Map<String, String> hashGetAllAndDelete(String key) {
        requireKey(key);
        List raw = redisTemplate.execute(HanRedisScripts.HASH_GET_ALL_AND_DELETE, List.of(key));
        Map<String, String> result = new LinkedHashMap<>();
        if (raw == null) {
            return result;
        }
        for (int i = 0; i + 1 < raw.size(); i += 2) {
            result.put(String.valueOf(raw.get(i)), String.valueOf(raw.get(i + 1)));
        }
        return result;
    }

    /**
     * 兜底修复：key 存在但没有 TTL 时补设过期时间。
     * <p>用于清理升级前遗留的永生 key —— 存量扫描请用 {@code SCAN}，不要用 {@code KEYS}。
     *
     * @return 是否实际补设了 TTL
     */
    public boolean ensureTtl(String key, Duration ttl) {
        requireKey(key);
        Long changed = redisTemplate.execute(HanRedisScripts.ENSURE_TTL,
                List.of(key), String.valueOf(toSeconds(ttl)));
        return changed != null && changed == 1L;
    }

    /**
     * 原子占位（{@code SET key value EX ttl NX}），用于幂等控制与轻量互斥。
     *
     * @return 是否抢到
     */
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        requireKey(key);
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Redis key 不能为空");
        }
    }

    private static long toSeconds(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("TTL 必须为正数，禁止写入没有过期时间的 key");
        }
        return Math.max(1L, ttl.getSeconds());
    }
}
