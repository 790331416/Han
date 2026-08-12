package com.han.tenant.converter;

import com.han.tenant.domain.dto.TenantDTO;
import com.han.tenant.domain.po.TenantPo;
import com.han.tenant.domain.vo.TenantOptionVO;
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
     * PO -> 下拉选项 VO（只含 ID 与名称）
     */
    @Mapping(source = "id", target = "tenantId")
    TenantOptionVO toOptionVO(TenantPo po);

    /**
     * PO List -> 下拉选项 VO List
     */
    List<TenantOptionVO> toOptionVOList(List<TenantPo> pos);

    /**
     * DTO -> PO
     */
    @Mapping(source = "tenantId", target = "id")
    TenantPo toPo(TenantDTO dto);

    /**
     * 更新实体（从解包后的 base PO 复制字段到已有 PO）
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "delFlag", ignore = true)
    @Mapping(target = "createBy", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    void updateFromBase(TenantPo source, @MappingTarget TenantPo target);
}
