package com.han.tenant.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     * JSON字符串 -> Set<Long>
     */
    @Named("jsonToSet")
    default Set<Long> jsonToSet(String json) {
        if (json == null || json.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            return OBJECT_MAPPER.readValue(json, new TypeReference<Set<Long>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptySet();
        }
    }

    /**
     * Set<Long> -> JSON字符串
     */
    @Named("setToJson")
    default String setToJson(Set<Long> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(set);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
