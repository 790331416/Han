package com.xuman.tenant.convert;

import com.xuman.tenant.domain.dto.TenantDTO;
import com.xuman.tenant.domain.entity.Tenant;
import com.xuman.tenant.domain.vo.TenantVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * 租户对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantConvert {

    /**
     * Entity -> VO
     */
    @Mapping(source = "id", target = "tenantId")
    TenantVO toVO(Tenant entity);

    /**
     * Entity List -> VO List
     */
    List<TenantVO> toVOList(List<Tenant> entities);

    /**
     * DTO -> Entity
     */
    @Mapping(source = "tenantId", target = "id")
    Tenant toEntity(TenantDTO dto);

    /**
     * 更新实体
     */
    @Mapping(target = "id", ignore = true)
    void updateEntity(TenantDTO dto, @MappingTarget Tenant entity);
}
