package com.han.tenant.converter;

import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.vo.TenantVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * 租户对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantConverter {

    /**
     * PO -> VO
     */
    @Mapping(source = "id", target = "tenantId")
    TenantVO toVO(TenantPo po);

    /**
     * PO List -> VO List
     */
    List<TenantVO> toVOList(List<TenantPo> pos);

    /**
     * DTO -> PO
     */
    @Mapping(source = "tenantId", target = "id")
    TenantPo toPo(TenantDTO dto);

    /**
     * 更新实体
     */
    @Mapping(target = "id", ignore = true)
    void updatePo(TenantDTO dto, @MappingTarget TenantPo po);
}
