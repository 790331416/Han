package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.*;

@AdminAuth
@RestController("adminSysMonitorController")
@RequestMapping("/system/monitor")
@RequiredArgsConstructor
public class ASysMonitorController {

    @GetMapping("/server")
    @PreAuthorize("@ss.hasAuthority('monitor:server:list')")
    public R<Map<String, Object>> server() {
        Map<String, Object> result = new LinkedHashMap<>();
        Runtime runtime = Runtime.getRuntime();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        RuntimeMXBean rt = ManagementFactory.getRuntimeMXBean();
        result.put("jvmTotal", runtime.totalMemory() / 1024 / 1024);
        result.put("jvmFree", runtime.freeMemory() / 1024 / 1024);
        result.put("jvmUsed", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024);
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("osName", os.getName());
        result.put("osArch", os.getArch());
        result.put("processors", os.getAvailableProcessors());
        result.put("uptime", rt.getUptime() / 1000);
        return R.ok(result);
    }

    @GetMapping("/cache")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:list')")
    public R<Map<String, Object>> cache() {
        return R.ok(Map.of("msg", "Redis monitoring not available in this module"));
    }

    @GetMapping("/cache/keys")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:list')")
    public R<List<String>> cacheKeys(@RequestParam(value = "pattern", defaultValue = "han:*") String pattern) {
        return R.ok(List.of());
    }

    @PostMapping("/cache/delete")
    @PreAuthorize("@ss.hasAuthority('monitor:cache:list')")
    public R<Void> deleteCache(@RequestBody Map<String, String> body) {
        return R.ok();
    }
}
