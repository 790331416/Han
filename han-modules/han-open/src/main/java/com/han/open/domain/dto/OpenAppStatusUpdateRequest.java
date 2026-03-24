package com.han.open.domain.dto;

import com.han.open.domain.po.OpenAppPo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 开放平台应用状态变更请求。
 */
@Data
public class OpenAppStatusUpdateRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 应用 ID。
     */
    private Long appId;

    /**
     * 兼容前端当前提交结构：{ appId, base: { status } }。
     */
    private OpenAppPo base;

    /**
     * 获取目标状态。
     */
    public Integer resolveStatus() {
        return base != null ? base.getStatus() : null;
    }
}
