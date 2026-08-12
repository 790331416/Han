package com.han.ai.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Knowledge hit test request.
 */
@Data
public class HitTestRequest {

    /**
     * 命中测试查询串。超长查询会原样打到 embedding 接口，必须限长。
     */
    @NotBlank(message = "命中测试查询内容不能为空")
    @Size(max = 1000, message = "命中测试查询内容不能超过 1000 字符")
    private String query;
}
