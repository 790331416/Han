package com.han.open.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.open.domain.po.OpenAppPo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 开放平台应用DTO（采用组合模式）
 * 
 * @author han Team
 */
@Data
public class OpenAppDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonUnwrapped
    private OpenAppPo base;

    /**
     * 兼容前端和文档中的 appId 命名，同时同步到底层 base.id。
     */
    @JsonProperty("appId")
    private Long appId;

    // ==================== 扩展字段 ====================

    /** 回调地址列表（前端传入） */
    private List<String> redirectUris;

    /** 授权范围列表（前端传入） */
    private List<String> scopes;

    /** 授权类型列表（前端传入） */
    private List<String> grantTypes;

    // ==================== 隐藏敏感字段 ====================

    @JsonIgnore
    public String getAppSecret() {
        return null;
    }

    // ==================== 核心业务字段便捷访问 ====================

    public Long getAppId() {
        if (appId != null) {
            return appId;
        }
        return base != null ? base.getId() : null;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
        ensureBase().setId(appId);
    }

    public OpenAppPo getBase() {
        if (base == null) {
            return null;
        }
        if (appId != null && base.getId() == null) {
            base.setId(appId);
        }
        return base;
    }

    public void setBase(OpenAppPo base) {
        this.base = base;
        this.appId = base != null ? base.getId() : null;
    }

    private OpenAppPo ensureBase() {
        if (base == null) {
            base = new OpenAppPo();
        }
        return base;
    }
}
