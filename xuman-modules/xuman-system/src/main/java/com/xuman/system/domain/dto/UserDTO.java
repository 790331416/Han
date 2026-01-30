package com.xuman.system.domain.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.xuman.system.domain.entity.User;
import lombok.Data;

import java.util.Set;

/**
 * 用户DTO（采用组合模式）
 * 
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>组合User实体，而非继承</li>
 *   <li>使用@JsonUnwrapped自动展开User字段</li>
 *   <li>扩展字段：角色ID、岗位ID等</li>
 *   <li>隐藏敏感字段：密码</li>
 * </ul>
 * 
 * <p><b>优势：</b>
 * <ul>
 *   <li>职责清晰：查询与持久化彻底分离</li>
 *   <li>精准控制：只暴露需要的字段</li>
 *   <li>无冲突风险：扩展字段不会与User冲突</li>
 * </ul>
 */
@Data
public class UserDTO {

    /**
     * 组合User实体（自动展开所有字段）
     * 
     * <p>序列化时，User内的字段会自动展开到根级别
     */
    @JsonUnwrapped
    private User base;

    // ====================  扩展字段 ====================

    /** 角色ID列表（业务扩展） */
    private Set<Long> roleIds;

    /** 岗位ID列表（业务扩展） */
    private Set<Long> postIds;

    // ==================== 隐藏敏感字段 ====================

    /**
     * 隐藏密码字段（防止序列化输出）
     */
    @JsonIgnore
    public String getPassword() {
        return null;
    }

    // ==================== 核心业务字段便捷访问 ====================

    /**
     * 获取用户ID
     */
    public Long getUserId() {
        return base != null ? base.getId() : null;
    }

    public void setUserId(Long userId) {
        if (base == null) {
            base = new User();
        }
        base.setId(userId);
    }
}
