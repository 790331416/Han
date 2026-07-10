package com.han.aivideo.service;

import com.han.aivideo.domain.dto.AivideoAdminSettingDto;
import com.han.aivideo.domain.vo.AivideoAdminSettingVo;

public interface IAivideoSettingService {

    AivideoAdminSettingVo getGlobalSetting();

    void saveGlobalSetting(AivideoAdminSettingDto dto);
}
