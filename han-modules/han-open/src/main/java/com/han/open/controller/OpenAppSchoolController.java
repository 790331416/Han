package com.han.open.controller;

import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.AdminAuth;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 厂商门户按应用已授权范围解析学校展示名称。 */
@AdminAuth
@RestController
@RequestMapping("/open/app")
@RequiredArgsConstructor
public class OpenAppSchoolController {

    private final IOpenAppService appService;
    private final SystemServiceClient systemServiceClient;

    @GetMapping("/{appId}/school-names")
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public R<Map<Long, String>> schoolNames(@PathVariable Long appId) {
        OpenAppVO app = appService.selectVoById(appId);
        if (app.getSchoolIds() == null || app.getSchoolIds().isEmpty()) {
            return R.ok(Map.of());
        }
        R<Map<Long, String>> response = systemServiceClient.getOpenSchoolNames(app.getTenantId(), app.getSchoolIds());
        if (response == null || response.isFail()) {
            throw new BusinessException(response == null ? "授权学校名称查询失败" : response.getMsg());
        }
        return R.ok(response.getData() == null ? Map.of() : response.getData());
    }
}
