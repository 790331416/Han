package com.han.system.controller.admin;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.domain.R;
import com.han.common.core.util.HanJsonUtil;
import com.han.common.security.annotation.AdminAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 在线用户管理 - A层（管理端控制器）
 */
@Slf4j
@AdminAuth
@RestController("adminSysOnlineController")
@RequestMapping("/system/online")
@RequiredArgsConstructor
public class ASysOnlineController {

    private final StringRedisTemplate redisTemplate;

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('monitor:online:list')")
    public R<List<Map<String, Object>>> list(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String ipAddr) {
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            Set<String> keys = redisTemplate.keys(CacheConstants.TOKEN_KEY + "*");
            if (keys != null) {
                for (String key : keys) {
                    String json = redisTemplate.opsForValue().get(key);
                    if (json == null) continue;
                    Map<String, Object> user = HanJsonUtil.parseMap(json);
                    String uname = String.valueOf(user.getOrDefault("username", ""));
                    String ip = String.valueOf(user.getOrDefault("loginIp", ""));

                    if (username != null && !username.isEmpty() && !uname.contains(username)) continue;
                    if (ipAddr != null && !ipAddr.isEmpty() && !ip.contains(ipAddr)) continue;

                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tokenId", key.replace(CacheConstants.TOKEN_KEY, ""));
                    item.put("userId", user.get("userId"));
                    item.put("username", uname);
                    item.put("nickname", String.valueOf(user.getOrDefault("nickname", "")));
                    item.put("ipAddr", ip);
                    item.put("clientType", String.valueOf(user.getOrDefault("clientType", "")));
                    item.put("loginTime", user.get("loginTime"));
                    list.add(item);
                }
            }
        } catch (Exception e) {
            log.error("获取在线用户列表失败", e);
        }
        list.sort((a, b) -> Long.compare(
                b.get("loginTime") != null ? (Long) b.get("loginTime") : 0,
                a.get("loginTime") != null ? (Long) a.get("loginTime") : 0));
        return R.ok(list);
    }

    @PostMapping("/forceLogout")
    @PreAuthorize("@ss.hasAuthority('monitor:online:forceLogout')")
    public R<Void> forceLogout(@RequestBody Map<String, String> body) {
        String tokenId = body.get("tokenId");
        if (tokenId != null && !tokenId.isEmpty()) {
            redisTemplate.delete(CacheConstants.TOKEN_KEY + tokenId);
        }
        return R.ok();
    }
}
