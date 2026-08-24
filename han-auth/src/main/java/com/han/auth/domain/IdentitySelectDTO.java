package com.han.auth.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 身份选择请求DTO（登录返回 requireIdentity 后，凭一次性票据选择身份换取正式 Token）。
 */
@Data
public class IdentitySelectDTO {

    /** 登录时下发的一次性身份票据 */
    @NotBlank(message = "身份票据不能为空")
    private String identityTicket;

    /** 选择的学校身份ID */
    @NotNull(message = "身份ID不能为空")
    private Long identityId;
}
