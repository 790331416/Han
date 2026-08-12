package com.han.tenant.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import com.han.tenant.domain.dto.TenantPackageDTO;
import com.han.tenant.domain.po.TenantPackagePo;
import com.han.tenant.domain.vo.TenantPackageVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 租户套餐对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantPackageConverter {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * PO -> VO
     */
    @Mapping(source = "id", target = "packageId")
    @Mapping(source = "menuIds", target = "menuIds", qualifiedByName = "jsonToSet")
    TenantPackageVO toVO(TenantPackagePo po);

    /**
     * PO List -> VO List
     */
    List<TenantPackageVO> toVOList(List<TenantPackagePo> pos);

    /**
     * DTO -> PO
     */
    @Mapping(source = "packageId", target = "id")
    @Mapping(source = "menuIds", target = "menuIds", qualifiedByName = "setToJson")
    TenantPackagePo toPo(TenantPackageDTO dto);

    /**
     * 更新实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(source = "menuIds", target = "menuIds", qualifiedByName = "setToJson")
    void updatePo(TenantPackageDTO dto, @MappingTarget TenantPackagePo po);

    /**
     * JSON字符串 -> Set&lt;Long&gt;
     * <p>
     * 解析失败必须响亮失败：套餐菜单会经 syncRoleMenus 覆盖租户角色菜单，
     * 一旦这里把损坏的 menu_ids 静默降级成空集，整个租户所有角色的菜单会被清空且不可恢复。
     */
    @Named("jsonToSet")
    default Set<Long> jsonToSet(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Set<Long>>() {});
        } catch (JsonProcessingException e) {
            throw new BusinessException("套餐菜单数据已损坏，无法解析: " + json);
        }
    }

    /**
     * Set&lt;Long&gt; -> JSON字符串
     */
    @Named("setToJson")
    default String setToJson(Set<Long> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(set);
        } catch (JsonProcessingException e) {
            throw new BusinessException("套餐菜单序列化失败");
        }
    }
}
