package com.han.auth.domain;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 身份切换请求DTO（已登录用户切换到当前账号的另一个有效学校身份）。
 */
@Data
public class IdentitySwitchDTO {

    /** 目标学校身份ID */
    @NotNull(message = "身份ID不能为空")
    private Long identityId;
}
