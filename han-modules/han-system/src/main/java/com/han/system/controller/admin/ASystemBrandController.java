package com.han.system.controller.admin;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.domain.dto.SystemBrandDto;
import com.han.system.domain.vo.SystemBrandSettingsVo;
import com.han.system.service.SystemBrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 由菜单权限控制的系统品牌设置。 */
@AdminAuth
@RestController
@RequestMapping("/system/brand")
@RequiredArgsConstructor
public class ASystemBrandController {

    private final SystemBrandService systemBrandService;

    @GetMapping
    @PreAuthorize("@ss.hasAuthority('system:brand:query')")
    public R<SystemBrandSettingsVo> getBrand() {
        return R.ok(systemBrandService.getSettings());
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('system:brand:edit')")
    @OperLog(module = "系统品牌设置", type = OperLog.OperType.UPDATE)
    public R<SystemBrandSettingsVo> updateBrand(@Valid @RequestBody SystemBrandDto form) {
        systemBrandService.updateBrand(form);
        return R.ok(systemBrandService.getSettings());
    }

    @RepeatSubmit
    @PostMapping("/logo")
    @PreAuthorize("@ss.hasAuthority('system:brand:edit')")
    @OperLog(module = "系统品牌设置", type = OperLog.OperType.UPDATE)
    public R<SystemBrandSettingsVo> updateLogo(@RequestPart("file") MultipartFile file) {
        systemBrandService.updateLogo(file);
        return R.ok(systemBrandService.getSettings());
    }
}
