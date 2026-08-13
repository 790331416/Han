package com.han.tenant.converter;

import com.han.api.tenant.domain.TenantVO;
import com.han.tenant.domain.po.TenantPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * 租户契约层对象转换器。
 * <p>
 * 只服务于 {@code /inner/tenant/**}，目标类型固定为契约包 {@link TenantVO}，
 * 与模块内展示用的 {@link com.han.tenant.domain.vo.TenantVO} 是两个不同的类型，不要混用。
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface TenantApiConverter {

    /**
     * PO -> 契约 VO
     */
    @Mapping(source = "id", target = "tenantId")
    TenantVO toApiVO(TenantPo po);

    /**
     * PO List -> 契约 VO List
     */
    List<TenantVO> toApiVOList(List<TenantPo> pos);
}
