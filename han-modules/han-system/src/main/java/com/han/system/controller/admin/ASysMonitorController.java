package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.AdminAuth;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;
import java.util.concurrent.TimeUnit;

@AdminAuth
@RestController("adminSysMonitorController")
@RequestMapping("/system/monitor")
@RequiredArgsConstructor
public class ASysMonitorController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/server")
    @PreAuthorize("@ss.hasAuthority('monitor:server:list')")
    public R<Map<String, Object>> server() {
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("javaVersion", valueOrDash(System.getProperty("java.version")));
        jvm.put("javaHome", valueOrDash(System.getProperty("java.home")));
        jvm.put("maxMemory", toMb(runtime.maxMemory()));
        jvm.put("totalMemory", toMb(runtime.totalMemory()));
        jvm.put("usedMemory", toMb(runtime.totalMemory() - runtime.freeMemory()));
        jvm.put("freeMemory", toMb(runtime.freeMemory()));
        jvm.put("heapUsed", toMb(heap.getUsed()));
        jvm.put("heapMax", toMb(heap.getMax()));
        jvm.put("uptime", rt.getUptime() / 1000);

        InetAddress host = localHost();
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("osName", valueOrDash(os.getName()));
        sys.put("osArch", valueOrDash(os.getArch()));
        sys.put("osVersion", valueOrDash(os.getVersion()));
        sys.put("availableProcessors", os.getAvailableProcessors());
        sys.put("systemLoadAverage", os.getSystemLoadAverage());
        sys.put("hostName", host == null ? "-" : valueOrDash(host.getHostName()));
        sys.put("hostAddress", host == null ? "-" : valueOrDash(host.getHostAddress()));
        sys.put("userDir", valueOrDash(System.getProperty("user.dir")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("jvm", jvm);
        result.put("sys", sys);
        return R.ok(result);
    }

    @GetMapping("/cache")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:list')")
    public R<Map<String, Object>> cache() {
        Map<String, Object> result = redisTemplate.execute((RedisCallback<Map<String, Object>>) connection -> {
            Properties info = connection.serverCommands().info();
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("redisVersion", infoValue(info, "redis_version"));
            values.put("uptimeInDays", infoLong(info, "uptime_in_days", infoLong(info, "uptime_in_seconds", 0L) / 86400));
            values.put("connectedClients", infoValue(info, "connected_clients"));
            values.put("usedMemory", infoValue(info, "used_memory_human"));
            values.put("usedMemoryPeak", infoValue(info, "used_memory_peak_human"));
            values.put("maxMemory", infoValue(info, "maxmemory_human"));
            values.put("totalCommandsProcessed", infoValue(info, "total_commands_processed"));
            values.put("instantaneousOpsPerSec", infoValue(info, "instantaneous_ops_per_sec"));
            values.put("dbSize", connection.serverCommands().dbSize());
            values.put("keyspaceHits", infoValue(info, "keyspace_hits"));
            values.put("keyspaceMisses", infoValue(info, "keyspace_misses"));
            return values;
        });
        return R.ok(result == null ? new LinkedHashMap<>() : result);
    }

    @GetMapping("/cache/keys")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:list')")
    public R<List<Map<String, Object>>> cacheKeys(@RequestParam(value = "pattern", defaultValue = "han:*") String pattern) {
        String match = StringUtils.hasText(pattern) ? pattern.trim() : "*";
        List<Map<String, Object>> keys = new ArrayList<>();
        try (Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(match).count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", key);
                item.put("ttl", redisTemplate.getExpire(key, TimeUnit.SECONDS));
                keys.add(item);
            }
        }
        keys.sort(Comparator.comparing(item -> String.valueOf(item.get("key"))));
        return R.ok(keys);
    }

    @PostMapping("/cache/delete")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:remove')")
    public R<Void> deleteCache(@RequestBody Map<String, String> body) {
        String key = body == null ? null : body.get("key");
        if (!StringUtils.hasText(key)) throw new BusinessException("缓存键不能为空");
        redisTemplate.delete(key.trim());
        return R.ok();
    }

    private static long toMb(long bytes) { return bytes < 0 ? 0 : bytes / 1024 / 1024; }

    private static String valueOrDash(String value) { return StringUtils.hasText(value) ? value : "-"; }

    private static String infoValue(Properties info, String key) { return valueOrDash(info == null ? null : info.getProperty(key)); }

    private static long infoLong(Properties info, String key, long fallback) {
        try { return Long.parseLong(info.getProperty(key)); } catch (Exception ignored) { return fallback; }
    }

    private static InetAddress localHost() {
        try { return InetAddress.getLocalHost(); } catch (Exception ignored) { return null; }
    }
}
