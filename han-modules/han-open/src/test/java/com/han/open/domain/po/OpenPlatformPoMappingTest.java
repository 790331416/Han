package com.han.open.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BaseEntity;
import com.han.common.mybatis.domain.entity.BizEntity;
import com.han.common.mybatis.domain.entity.TenantEntity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 开放平台 PO 与冻结表结构的映射一致性测试。
 * <p>仅通过 Java 反射验证注解/字段/继承关系，不连接数据库、不使用 Mock。</p>
 */
class OpenPlatformPoMappingTest {

    @Test
    void vendorUserPoInheritsTenantEntityAndKeepsVendorIdAsPlainField() throws NoSuchFieldException {
        assertThat(OpenVendorUserPo.class.getSuperclass()).isEqualTo(TenantEntity.class);

        // vendorId 自身不再标注 @TableId（主键改由 BaseEntity.id 承担）
        Field vendorId = OpenVendorUserPo.class.getDeclaredField("vendorId");
        assertThat(vendorId.getAnnotation(TableId.class)).isNull();

        // 手写的 createTime/updateTime 不应残留在自身，统一由 BaseEntity 继承
        assertThatThrownBy(() -> OpenVendorUserPo.class.getDeclaredField("createTime"))
                .isInstanceOf(NoSuchFieldException.class);
        assertThatThrownBy(() -> OpenVendorUserPo.class.getDeclaredField("updateTime"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void vendorUserInheritsSnowflakeIdAndLogicDeleteFromBaseEntity() throws NoSuchFieldException {
        Field id = BaseEntity.class.getDeclaredField("id");
        TableId tableId = id.getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.ASSIGN_ID);

        Field delFlag = BaseEntity.class.getDeclaredField("delFlag");
        assertThat(delFlag.getAnnotation(TableLogic.class)).isNotNull();
    }

    @Test
    void vendorPoDoesNotRedeclareTenantIdButInheritsIt() throws NoSuchFieldException {
        assertThat(BizEntity.class.isAssignableFrom(OpenVendorPo.class)).isTrue();

        // tenantId 不应在 OpenVendorPo 自身重复声明
        assertThatThrownBy(() -> OpenVendorPo.class.getDeclaredField("tenantId"))
                .isInstanceOf(NoSuchFieldException.class);

        // tenantId 由父类 TenantEntity 提供
        Field tenantId = TenantEntity.class.getDeclaredField("tenantId");
        assertThat(tenantId).isNotNull();
        assertThat(tenantId.getType()).isEqualTo(Long.class);
    }

    @Test
    void grantPoExtendsBizEntityAndDeclaresEnvironment() throws NoSuchFieldException {
        assertThat(BizEntity.class.isAssignableFrom(OpenAppResourceGrantPo.class)).isTrue();

        Field environment = OpenAppResourceGrantPo.class.getDeclaredField("environment");
        assertThat(environment.getType()).isEqualTo(String.class);
    }

    @Test
    void tableNameAnnotationsMatchFrozenSchema() {
        assertTableName(OpenVendorPo.class, "open_vendor");
        assertTableName(OpenVendorUserPo.class, "open_vendor_user");
        assertTableName(OpenVendorApplicationPo.class, "open_vendor_application");
        assertTableName(OpenAppPo.class, "open_app");
        assertTableName(OpenAppResourceGrantPo.class, "open_app_resource_grant");
        assertTableName(OpenAuthorizationRequestPo.class, "open_authorization_request");
        assertTableName(OpenAppCredentialPo.class, "open_app_credential");
        assertTableName(OpenApiResourceVersionPo.class, "open_api_resource_version");
    }

    @Test
    void tenantPersistenceObjectsExtendBizEntity() {
        assertThat(BizEntity.class.isAssignableFrom(OpenVendorPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenVendorApplicationPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenAppPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenAppResourceGrantPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenAuthorizationRequestPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenAppCredentialPo.class)).isTrue();
    }

    @Test
    void apiResourceVersionPoExtendsBaseEntityButNotBizEntity() throws NoSuchFieldException {
        // 全局目录版本表无租户维度，只继承最小基类 BaseEntity，不再属于 BizEntity
        assertThat(BaseEntity.class.isAssignableFrom(OpenApiResourceVersionPo.class)).isTrue();
        assertThat(BizEntity.class.isAssignableFrom(OpenApiResourceVersionPo.class)).isFalse();

        // 自身不应再声明 tenantId
        assertThatThrownBy(() -> OpenApiResourceVersionPo.class.getDeclaredField("tenantId"))
                .isInstanceOf(NoSuchFieldException.class);
    }

    @Test
    void apiTestRunUsesExplicitInputIdAndFrozenAuditTable() throws NoSuchFieldException {
        assertTableName(OpenApiTestRunPo.class, "open_api_test_run");
        TableId tableId = OpenApiTestRunPo.class.getDeclaredField("id").getAnnotation(TableId.class);
        assertThat(tableId).isNotNull();
        assertThat(tableId.type()).isEqualTo(IdType.INPUT);
        assertThat(OpenApiTestRunPo.class.getDeclaredField("redactedSummary").getType())
                .isEqualTo(String.class);
        assertThat(OpenApiTestRunPo.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("responseBody")
                        || field.getName().equals("requestHeaders")
                        || field.getName().equals("clientSecret"));
    }

    private static void assertTableName(Class<?> po, String expected) {
        TableName annotation = po.getAnnotation(TableName.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
