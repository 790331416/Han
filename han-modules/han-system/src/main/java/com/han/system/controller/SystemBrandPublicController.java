package com.han.system.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.PermissionExempt;
import com.han.system.domain.vo.SystemBrandVo;
import com.han.system.service.SystemBrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 登录前可读取的品牌展示信息；仅包含名称与副标题，不返回任意系统参数。 */
@RestController
@RequestMapping("/system/public/brand")
@RequiredArgsConstructor
public class SystemBrandPublicController {

    private final SystemBrandService systemBrandService;

    @GetMapping
    @PermissionExempt("登录页读取非敏感系统品牌信息")
    public R<SystemBrandVo> getPublicBrand() {
        return R.ok(systemBrandService.getBrand());
    }

    @GetMapping("/logo")
    @PermissionExempt("登录页读取非敏感系统品牌Logo")
    public ResponseEntity<byte[]> getPublicLogo() {
        return systemBrandService.getLogo()
                .map(logo -> ResponseEntity.ok()
                        .cacheControl(CacheControl.noStore())
                        .contentType(MediaType.parseMediaType(logo.contentType()))
                        .body(logo.content()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
