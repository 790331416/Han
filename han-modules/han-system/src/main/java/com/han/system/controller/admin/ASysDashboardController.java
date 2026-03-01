package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.system.mapper.SysDeptMapper;
import com.han.system.mapper.SysPostMapper;
import com.han.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@AdminAuth
@RestController("adminSysDashboardController")
@RequestMapping("/system/dashboard")
@RequiredArgsConstructor
public class ASysDashboardController {

    private final SysUserMapper userMapper;
    private final SysDeptMapper deptMapper;
    private final SysPostMapper postMapper;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userCount", userMapper.selectCount(null));
        data.put("deptCount", deptMapper.selectCount(null));
        data.put("postCount", postMapper.selectCount(null));
        data.put("onlineCount", 1);
        return R.ok(data);
    }
}
