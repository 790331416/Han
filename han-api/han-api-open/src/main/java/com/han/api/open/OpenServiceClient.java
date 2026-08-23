package com.han.api.open;

import com.han.api.open.domain.OpenVendorApplicationCreateDTO;
import com.han.api.open.domain.OpenVendorApplicationStatusVO;
import com.han.common.core.domain.R;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** han-auth 调用 han-open 的厂商门户内部客户端。 */
@HttpExchange("/inner/open/vendor")
public interface OpenServiceClient {

    @PostExchange("/application")
    R<String> createPortalApplication(@RequestBody OpenVendorApplicationCreateDTO dto);

    @GetExchange("/application/status")
    R<OpenVendorApplicationStatusVO> queryPortalApplication(
            @RequestParam("contactPhone") String contactPhone);
}
