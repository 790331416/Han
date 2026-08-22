package com.han.open.domain.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import java.util.List;

/**
 * 授权申请VO
 */
@Data
public class GrantApplyVO {

    /** 应用ID */
    @NotNull(message = "应用ID不能为空")
    private Long appId;

    /** 环境类型：SANDBOX沙箱、PROD生产 */
    @NotBlank(message = "环境类型不能为空")
    @Pattern(regexp = "SANDBOX|PROD", message = "环境类型仅支持SANDBOX或PROD")
    private String environment;

    /** 申请的资源列表 */
    @NotEmpty(message = "申请的资源列表不能为空")
    private List<ResourceApplyItem> resources;

    /** 申请理由 */
    @NotBlank(message = "申请理由不能为空")
    private String applyReason;

    /**
     * 资源申请项
     */
    @Data
    public static class ResourceApplyItem {
        /** 资源ID */
        @NotNull(message = "资源ID不能为空")
        private Long resourceId;

        /** 申请的Scope列表，逗号分隔 */
        @NotBlank(message = "授权Scope不能为空")
        private String scopes;

        /** 数据范围配置 */
        private String dataScope;

        /** 调用配额，0表示不限制 */
        private Long quota = 0L;

        /** 有效期（天），0表示永久有效 */
        private Integer expireDays = 0;
    }
}
