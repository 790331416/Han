package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 可授权的开放接口目录项。
 */
@Data
@TableName("open_api_resource")
public class OpenApiResourcePo implements Serializable {

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
    private Integer sort;

    /**
     * 发布状态：0草稿 1待审核 2已发布 3已下线
     */
    private Integer publishStatus;

    /**
     * 是否允许厂商申请：0否 1是
     */
    private Integer allowApply;

    /**
     * 是否允许在线调测：0否 1是
     */
    private Integer allowTest;

    /**
     * 负责人
     */
    private String owner;

    /**
     * 版本列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<OpenApiResourceVersionPo> versions;
}
