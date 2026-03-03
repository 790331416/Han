package com.han.system.controller.inner;

import com.han.api.system.domain.LoginLogDTO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.SysLoginLogPo;
import com.han.system.service.ISysLoginLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 登录日志 - I层（内部控制器）
 */
@InnerAuth
@RestController("innerSysLoginLogController")
@RequestMapping("/inner/system/loginlog")
@RequiredArgsConstructor
public class ISysLoginLogController {

    private final ISysLoginLogService loginLogService;

    @PostMapping("/record")
    public R<Void> record(@RequestBody LoginLogDTO dto) {
        SysLoginLogPo po = SysLoginLogPo.builder()
                .username(dto.getUsername())
                .tenantId(dto.getTenantId())
                .ipAddr(dto.getIpAddr())
                .loginLocation(dto.getLoginLocation())
                .status(dto.getStatus())
                .message(dto.getMessage())
                .clientType(dto.getClientType())
                .browser(dto.getBrowser())
                .os(dto.getOs())
                .loginTime(LocalDateTime.now())
                .build();
        loginLogService.insertLoginLog(po);
        return R.ok();
    }
}
