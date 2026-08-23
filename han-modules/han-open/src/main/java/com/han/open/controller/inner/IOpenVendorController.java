package com.han.open.controller.inner;

import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.open.service.OpenVendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 厂商门户内部接口，仅供 han-auth 编排公开注册和状态查询。 */
@InnerAuth
@RestController("innerOpenVendorController")
@RequestMapping("/inner/open/vendor")
@RequiredArgsConstructor
public class IOpenVendorController {

    private final OpenVendorService vendorService;

    @PostMapping("/application")
    public R<String> createPortalApplication(@RequestBody OpenVendorApplicationCreateDTO dto) {
        return R.ok(vendorService.createPortalApplication(dto));
    }

    @GetMapping("/application/status")
    public R<OpenVendorApplicationStatusVO> queryPortalApplication(@RequestParam String contactPhone) {
        return R.ok(vendorService.queryPublicApplication(contactPhone));
    }
}
