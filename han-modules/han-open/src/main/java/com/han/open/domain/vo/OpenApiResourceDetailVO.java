package com.han.open.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 开放接口资源详情VO
 */
@Data
public class OpenApiResourceDetailVO {

    private Long id;
    private String resourceCode;
    private String resourceName;
    private String category;
    private String httpMethod;
    private String path;
    private String scopeCode;
    private String description;
    private String sensitivity;
    private Integer status;
    private Integer publishStatus;
    private Integer allowApply;
    private Integer allowTest;
    private String owner;
    private Integer sort;

    /**
     * 当前生效版本信息
     */
    private OpenApiResourceVersionVO currentVersion;

    /**
     * 所有版本列表
     */
    private List<OpenApiResourceVersionVO> versions;
}
