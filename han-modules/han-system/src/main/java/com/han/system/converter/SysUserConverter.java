package com.han.system.converter;

import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * 用户对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface SysUserConverter {

    @Mapping(source = "id", target = "userId")
    SysUserDto toDto(SysUserPo po);

    @Mapping(source = "userId", target = "id")
    SysUserPo toPo(SysUserDto dto);

    @Mapping(source = "id", target = "userId")
    UserVO toVo(SysUserPo po);

    /**
     * DTO -> PO（更新已有实体）
     *
     * <p>密码不走编辑入口：改密只允许通过 resetPwd / updatePassword，
     * 避免编辑用户时把密码覆盖成空值或把明文写进库。
     */
    @Mapping(source = "userId", target = "id")
    @Mapping(target = "password", ignore = true)
    void updatePo(SysUserDto dto, @MappingTarget SysUserPo po);
}
