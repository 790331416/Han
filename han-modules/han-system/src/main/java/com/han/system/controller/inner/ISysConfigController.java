package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.SysConfigPo;
import com.han.system.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 参数配置 - I层（内部控制器）
 * <p>
 * 供其他服务读取系统参数（如 auth 服务读取验证码开关）。
 */
@InnerAuth
@RestController("innerSysConfigController")
@RequestMapping("/inner/system/config")
@RequiredArgsConstructor
public class ISysConfigController {

    private final SysConfigMapper configMapper;

    /**
     * 按 key 查询参数值；不存在时返回空字符串。
     */
    @GetMapping("/value")
    public R<String> getValue(@RequestParam("configKey") String configKey) {
        SysConfigPo config = configMapper.selectOne(new LambdaQueryWrapper<SysConfigPo>()
                .eq(SysConfigPo::getConfigKey, configKey)
                .last("LIMIT 1"));
        return R.ok(config != null ? config.getConfigValue() : "");
    }
}
