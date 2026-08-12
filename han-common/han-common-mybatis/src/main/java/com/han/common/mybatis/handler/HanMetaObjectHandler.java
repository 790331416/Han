package com.han.common.mybatis.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.han.common.core.context.SecurityContext;
import com.han.common.mybatis.config.TenantProperties;
import com.han.common.mybatis.domain.entity.BaseEntity;
import com.han.common.mybatis.domain.entity.BizEntity;
import com.han.common.mybatis.domain.entity.TenantEntity;
import com.han.common.tenant.enums.MissingTenantContextStrategy;
import com.han.common.tenant.exception.MissingTenantContextException;
import com.han.common.tenant.observe.MissingTenantContextRecorder;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>自动填充 createTime、updateTime、tenantId、createBy、updateBy、createDept 等审计字段。</p>
 *
 * <p>无租户上下文时的 tenantId 填充按 {@code tenant.missing-context} 处置：默认
 * {@link MissingTenantContextStrategy#IGNORE} 保持历史行为（不填，落库后 tenant_id 为空，
 * 任何租户都看不见），但会记录到观测器；切到 {@link MissingTenantContextStrategy#REJECT}
 * 后直接拒绝写入，避免继续产生孤儿数据。排除清单里的表与显式忽略租户的写入不在此列。</p>
 */
@Slf4j
public class HanMetaObjectHandler implements MetaObjectHandler {

    /** 观测日志中标识这是一次插入填充 */
    private static final String OPERATION_INSERT = "INSERT";

    private final SecurityContext securityContext;
    private final TenantProperties tenantProperties;
    private final HanTenantLineHandler tenantLineHandler;
    private final MissingTenantContextRecorder missingContextRecorder;

    public HanMetaObjectHandler(SecurityContext securityContext) {
        this(securityContext, null, null, null);
    }

    public HanMetaObjectHandler(SecurityContext securityContext,
                                TenantProperties tenantProperties,
                                HanTenantLineHandler tenantLineHandler,
                                MissingTenantContextRecorder missingContextRecorder) {
        this.securityContext = securityContext;
        this.tenantProperties = tenantProperties;
        this.tenantLineHandler = tenantLineHandler;
        this.missingContextRecorder = missingContextRecorder == null
                ? new MissingTenantContextRecorder(false, 0L)
                : missingContextRecorder;
    }

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
                    if (tenantId == null) {
                        handleMissingTenantOnInsert(tenantEntity);
                    } else {
                        tenantEntity.setTenantId(tenantId);
                    }
                }
            } else if (metaObject.hasSetter("tenantId") && metaObject.getValue("tenantId") == null) {
                if (tenantId == null) {
                    handleMissingTenantOnInsert(metaObject.getOriginalObject());
                } else {
                    metaObject.setValue("tenantId", tenantId);
                }
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
        } catch (MissingTenantContextException e) {
            // 这是按策略主动拒绝，不能被当成填充异常吞掉
            throw e;
        } catch (Exception e) {
            log.error("自动填充插入字段异常: entity={}", entityName(metaObject), e);
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
            log.error("自动填充更新字段异常: entity={}", entityName(metaObject), e);
        }
    }

    /**
     * 无租户上下文却要写入带 tenant_id 的表：记录观测，并按策略决定是否拒绝。
     *
     * <p>两类场景不作处置：一是排除清单里的表（本就不参与租户隔离，例如登录日志），
     * 二是调用方已经显式声明忽略租户（{@code @IgnoreTenant} 或 {@code TenantHelper.ignore}）。</p>
     */
    private void handleMissingTenantOnInsert(Object entity) {
        if (InterceptorIgnoreHelper.hasIgnoreStrategy()) {
            return;
        }

        String tableName = resolveTableName(entity);
        if (tableName != null && tenantLineHandler != null && tenantLineHandler.isExcludedTable(tableName)) {
            return;
        }

        String target = tableName != null ? tableName : entity.getClass().getSimpleName();
        missingContextRecorder.record(OPERATION_INSERT, target);

        if (strategy() == MissingTenantContextStrategy.REJECT) {
            throw new MissingTenantContextException("缺少租户上下文，拒绝写入租户隔离表: " + target);
        }
    }

    private MissingTenantContextStrategy strategy() {
        if (tenantProperties == null || tenantProperties.getMissingContext() == null) {
            return MissingTenantContextStrategy.IGNORE;
        }
        return tenantProperties.getMissingContext();
    }

    private String resolveTableName(Object entity) {
        if (entity == null) {
            return null;
        }
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity.getClass());
        return tableInfo == null ? null : tableInfo.getTableName();
    }

    private String entityName(MetaObject metaObject) {
        Object entity = metaObject == null ? null : metaObject.getOriginalObject();
        return entity == null ? "unknown" : entity.getClass().getName();
    }
}
