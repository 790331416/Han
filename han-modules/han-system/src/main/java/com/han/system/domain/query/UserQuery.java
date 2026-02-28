package com.han.system.domain.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.common.core.domain.query.TenantQuery;
import com.han.system.domain.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户查询对象（采用组合模式）
 * 
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>组合User实体，而非继承</li>
 *   <li>查询与持久化彻底分离</li>
 *   <li>扩展查询字段：时间范围、数据权限等</li>
 *   <li>隐藏敏感字段：密码等</li>
 * </ul>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserQuery extends TenantQuery {

    /**
     * 组合User实体
     */
    @JsonUnwrapped
    private User base;

    // ==================== 查询专属字段 ====================

    /** 开始时间（范围查询） */
    @Schema(description = "开始时间")
    private LocalDateTime beginTime;

    /** 结束时间（范围查询） */
    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    /** 部门ID列表（数据权限） */
    @Schema(description = "数据权限部门ID列表", hidden = true)
    private List<Long> deptIds;

    // ==================== 隐藏敏感字段 ====================

    /**
     * 隐藏密码字段（查询时不应该传入密码）
     */
    @JsonIgnore
    @Schema(hidden = true)
    public String getPassword() {
        return null;  // 查询不需要密码
    }

    public void setPassword(String password) {
        // 忽略，查询时不应该设置密码
    }
}
