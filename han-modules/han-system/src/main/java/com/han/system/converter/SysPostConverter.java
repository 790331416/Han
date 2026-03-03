package com.han.system.converter;

import com.han.system.domain.dto.SysPostDto;
import com.han.system.domain.po.SysPostPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 岗位对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SysPostConverter {

    /**
     * DTO -> PO（新增）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "updateBy", ignore = true)
    @Mapping(target = "createName", ignore = true)
    @Mapping(target = "updateName", ignore = true)
    @Mapping(target = "createDept", ignore = true)
    SysPostPo toPo(SysPostDto dto);

    /**
     * DTO -> PO（更新已有实体）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createName", ignore = true)
    @Mapping(target = "createDept", ignore = true)
    void updatePo(SysPostDto dto, @MappingTarget SysPostPo po);
}
