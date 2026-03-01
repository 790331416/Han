package com.han.system.converter;

import com.han.system.domain.dto.SysDeptDto;
import com.han.system.domain.po.SysDeptPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 部门对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SysDeptConverter {

    /**
     * DTO -> PO（新增）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ancestors", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createName", ignore = true)
    @Mapping(target = "updateName", ignore = true)
    @Mapping(target = "createDept", ignore = true)
    @Mapping(target = "orderNum", ignore = true)
    @Mapping(source = "sort", target = "sort")
    SysDeptPo toPo(SysDeptDto dto);

    /**
     * DTO -> PO（更新已有实体）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ancestors", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createName", ignore = true)
    @Mapping(target = "createDept", ignore = true)
    @Mapping(target = "orderNum", ignore = true)
    void updatePo(SysDeptDto dto, @MappingTarget SysDeptPo po);
}
