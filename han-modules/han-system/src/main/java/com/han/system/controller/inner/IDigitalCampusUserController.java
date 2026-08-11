package com.han.system.controller.inner;

import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.api.system.domain.UserVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.sdfz.digitalcampus.DigitalCampusIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数字校园用户即时同步内部接口。
 */
@InnerAuth
@RestController
@RequestMapping("/inner/system/external/digital-campus")
@RequiredArgsConstructor
public class IDigitalCampusUserController {

    private final DigitalCampusIdentityService identityService;

    @PostMapping("/user/sync")
    public R<UserVO> syncCurrentUser(@RequestBody DigitalCampusUserSyncDTO dto) {
        return R.ok(identityService.syncCurrentUser(dto));
    }
}
