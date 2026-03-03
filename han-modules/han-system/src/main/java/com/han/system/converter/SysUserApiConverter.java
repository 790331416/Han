package com.han.system.converter;

import com.han.api.system.domain.UserVO;
import com.han.system.domain.po.SysUserPo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * SysUserPo → API UserVO 转换器（供 Inner 层 RPC 接口使用）
 */
@Mapper(componentModel = "spring")
public interface SysUserApiConverter {

    @Mapping(source = "id", target = "userId")
    @Mapping(target = "roleIds", ignore = true)
    @Mapping(target = "roleKeys", ignore = true)
    @Mapping(target = "permissions", ignore = true)
    UserVO toApiUserVO(SysUserPo po);
}
