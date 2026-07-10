package com.han.aivideo.controller.admin;

import com.han.aivideo.controller.base.BAivideoAdminSettingController;
import com.han.aivideo.domain.dto.AivideoAdminSettingDto;
import com.han.aivideo.domain.vo.AivideoAdminSettingVo;
import com.han.aivideo.service.IAivideoSettingService;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@AdminAuth
@RestController("aivideoAdminSettingController")
@RequestMapping("/aivideo/admin/setting")
public class AAivideoSettingController extends BAivideoAdminSettingController {

    public AAivideoSettingController(IAivideoSettingService settingService) {
        super(settingService);
    }

    @GetMapping
    @PreAuthorize("@ss.hasAuthority('ai:aivideo:setting:query')")
    public R<AivideoAdminSettingVo> getInfo() {
        return getSetting();
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('ai:aivideo:setting:edit')")
    public R<Void> edit(@Valid @RequestBody AivideoAdminSettingDto dto) {
        return editSetting(dto);
    }
}
