package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.domain.entity.BaseEntity;
import com.han.common.mybatis.domain.entity.BizEntity;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>自动填充 createTime、updateTime、tenantId、createBy、updateBy、createDept 等审计字段。</p>
 */
@Slf4j
@RequiredArgsConstructor
public class HanMetaObjectHandler implements MetaObjectHandler {

    private final SecurityContext securityContext;

    @Override
    public void insertFill(MetaObject metaObject) {
        try {
            Long tenantId = securityContext.getTenantId();

            if (metaObject.getOriginalObject() instanceof BaseEntity entity) {
                LocalDateTime now = LocalDateTime.now();
                entity.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime() : now);
                entity.setUpdateTime(now);
            }

            if (metaObject.getOriginalObject() instanceof TenantEntity tenantEntity) {
                if (tenantEntity.getTenantId() == null) {
                    tenantEntity.setTenantId(tenantId);
                }
            } else if (tenantId != null && metaObject.hasSetter("tenantId") && metaObject.getValue("tenantId") == null) {
                metaObject.setValue("tenantId", tenantId);
            }

            if (metaObject.getOriginalObject() instanceof BizEntity bizEntity) {
                if (bizEntity.getCreateBy() == null && securityContext.isLogin()) {
                    bizEntity.setCreateBy(securityContext.getUserId());
                    bizEntity.setUpdateBy(securityContext.getUserId());
                    bizEntity.setCreateName(securityContext.getNickname());
                    bizEntity.setUpdateName(securityContext.getNickname());
                    if (bizEntity.getCreateDept() == null) {
                        bizEntity.setCreateDept(securityContext.getDeptId());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("自动填充插入字段异常: {}", e.getMessage());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        try {
            if (metaObject.getOriginalObject() instanceof BaseEntity entity) {
                entity.setUpdateTime(LocalDateTime.now());
            }

            if (metaObject.getOriginalObject() instanceof BizEntity bizEntity) {
                if (securityContext.isLogin()) {
                    bizEntity.setUpdateBy(securityContext.getUserId());
                    bizEntity.setUpdateName(securityContext.getNickname());
                }
            }
        } catch (Exception e) {
            log.warn("自动填充更新字段异常: {}", e.getMessage());
        }
    }
}
