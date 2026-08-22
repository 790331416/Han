package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 开放接口资源版本
 * <p>全局目录版本表：无租户维度，仅继承最小基类 BaseEntity，自带雪花 id / del_flag / create_time / update_time。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("open_api_resource_version")
public class OpenApiResourceVersionPo extends BaseEntity {

    /**
     * 关联资源ID
     */
    private Long resourceId;

    /**
     * 版本号，如v1、v2
     */
    private String version;

    /**
     * OpenAPI 3.1 JSON契约
     */
    private String openapiJson;

    /**
     * 请求实例JSON
     */
    private String requestExampleJson;

    /**
     * 响应实例JSON，包含成功和常见失败响应
     */
    private String responseExamplesJson;

    /**
     * 错误实例JSON
     */
    private String errorExamplesJson;

    /**
     * 认证配置JSON
     */
    private String authConfigJson;

    /**
     * 沙箱配置JSON
     */
    private String sandboxConfigJson;

    /**
     * 状态：0草稿 1已发布 2已废弃
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishedAt;

    /**
     * 废弃时间
     */
    private LocalDateTime deprecatedAt;

    /**
     * 创建者ID
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 更新者ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;
}
