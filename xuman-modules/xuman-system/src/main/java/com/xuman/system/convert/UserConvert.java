package com.xuman.system.convert;

import com.xuman.system.domain.dto.UserDTO;
import com.xuman.system.domain.entity.User;
import com.xuman.system.domain.vo.UserVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

/**
 * 用户对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserConvert {

    /**
     * Entity -> VO
     */
    @Mapping(source = "id", target = "userId")
    @Mapping(source = "tenantId", target = "tenantId")
    UserVO toVO(User entity);

    /**
     * Entity List -> VO List
     */
    List<UserVO> toVOList(List<User> entities);

    /**
     * DTO -> Entity (新增时使用)
     * 注意：password需要单独加密处理
     */
    @Mapping(source = "userId", target = "id")
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    User toEntity(UserDTO dto);

    /**
     * 更新实体（仅更新非null字段）
     * 注意：不更新username和password
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    void updateEntity(UserDTO dto, @MappingTarget User entity);
}
