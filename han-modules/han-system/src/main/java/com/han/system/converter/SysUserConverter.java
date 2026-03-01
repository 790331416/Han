package com.han.system.converter;

import com.han.system.domain.dto.SysUserDto;
import com.han.system.domain.po.SysUserPo;
import com.han.system.domain.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

/**
 * 用户对象转换器
 */
@Mapper(componentModel = "spring")
public interface SysUserConverter {

    @Mapping(source = "id", target = "userId")
    SysUserDto toDto(SysUserPo po);

    @Mapping(source = "userId", target = "id")
    SysUserPo toPo(SysUserDto dto);

    @Mapping(source = "id", target = "userId")
    UserVO toVo(SysUserPo po);

    @Mapping(source = "userId", target = "id")
    void updatePo(SysUserDto dto, @MappingTarget SysUserPo po);
}
