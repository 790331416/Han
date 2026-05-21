package com.han.aivideo.controller.base;

import com.han.aivideo.domain.dto.AivideoAdminSettingDto;
import com.han.aivideo.domain.vo.AivideoAdminSettingVo;
import com.han.aivideo.service.IAivideoSettingService;
import com.han.common.core.domain.R;

public class BAivideoAdminSettingController {

    private final IAivideoSettingService settingService;

    protected BAivideoAdminSettingController(IAivideoSettingService settingService) {
        this.settingService = settingService;
    }

    protected R<AivideoAdminSettingVo> getSetting() {
        return R.ok(settingService.getGlobalSetting());
    }

    protected R<Void> editSetting(AivideoAdminSettingDto dto) {
        settingService.saveGlobalSetting(dto);
        return R.ok();
    }
}
