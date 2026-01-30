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
 * 用户对象转换器（支持组合模式）
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserConvert {

    /**
     * Entity -> DTO（组合模式）
     */
    @Mapping(source = ".", target = "base")
    UserDTO toDto(User entity);

    /**
     * Entity List -> DTO List
     */
    List<UserDTO> toDtoList(List<User> entities);

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
     * DTO -> Entity（组合模式处理）
     * 注意：password需要单独加密处理
     */
    @Mapping(source = "base", target = ".")
    @Mapping(target = "password", ignore = true)
    User toEntity(UserDTO dto);

    /**
     * 更新实体（组合模式处理，仅更新非null字段）
     * 注意：不更新username和password
     */
    @Mapping(source = "base.deptId", target = "deptId")
    @Mapping(source = "base.nickname", target = "nickname")
    @Mapping(source = "base.phone", target = "phone")
    @Mapping(source = "base.email", target = "email")
    @Mapping(source = "base.sex", target = "sex")
    @Mapping(source = "base.status", target = "status")
    @Mapping(source = "base.remark", target = "remark")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "loginIp", ignore = true)
    @Mapping(target = "loginTime", ignore = true)
    void updateEntity(UserDTO dto, @MappingTarget User entity);
}
