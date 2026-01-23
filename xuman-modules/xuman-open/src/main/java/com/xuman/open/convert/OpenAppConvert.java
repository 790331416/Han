package com.xuman.open.convert;

import com.xuman.open.domain.dto.OpenAppDTO;
import com.xuman.open.domain.entity.OpenApp;
import com.xuman.open.domain.vo.OpenAppVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 开放平台应用对象转换器
 */
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface OpenAppConvert {

    /**
     * Entity -> VO
     */
    @Mapping(source = "id", target = "appId")
    @Mapping(source = "redirectUris", target = "redirectUris", qualifiedByName = "stringToList")
    @Mapping(source = "scopes", target = "scopes", qualifiedByName = "stringToList")
    @Mapping(source = "grantTypes", target = "grantTypes", qualifiedByName = "stringToList")
    OpenAppVO toVO(OpenApp entity);

    /**
     * Entity List -> VO List
     */
    List<OpenAppVO> toVOList(List<OpenApp> entities);

    /**
     * DTO -> Entity
     */
    @Mapping(source = "appId", target = "id")
    @Mapping(source = "redirectUris", target = "redirectUris", qualifiedByName = "listToString")
    @Mapping(source = "scopes", target = "scopes", qualifiedByName = "listToString")
    @Mapping(source = "grantTypes", target = "grantTypes", qualifiedByName = "listToString")
    @Mapping(source = "requirePkce", target = "requirePkce", qualifiedByName = "boolToInt")
    @Mapping(source = "autoApprove", target = "autoApprove", qualifiedByName = "boolToInt")
    @Mapping(target = "appKey", ignore = true)
    @Mapping(target = "appSecret", ignore = true)
    OpenApp toEntity(OpenAppDTO dto);

    /**
     * 更新实体
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "appKey", ignore = true)
    @Mapping(target = "appSecret", ignore = true)
    @Mapping(source = "redirectUris", target = "redirectUris", qualifiedByName = "listToString")
    @Mapping(source = "scopes", target = "scopes", qualifiedByName = "listToString")
    @Mapping(source = "grantTypes", target = "grantTypes", qualifiedByName = "listToString")
    @Mapping(source = "requirePkce", target = "requirePkce", qualifiedByName = "boolToInt")
    @Mapping(source = "autoApprove", target = "autoApprove", qualifiedByName = "boolToInt")
    void updateEntity(OpenAppDTO dto, @MappingTarget OpenApp entity);

    /**
     * 逗号分隔字符串 -> List
     */
    @Named("stringToList")
    default List<String> stringToList(String str) {
        if (str == null || str.isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.asList(str.split(","));
    }

    /**
     * List -> 逗号分隔字符串
     */
    @Named("listToString")
    default String listToString(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(",", list);
    }

    /**
     * Boolean -> Integer
     */
    @Named("boolToInt")
    default Integer boolToInt(Boolean bool) {
        if (bool == null) {
            return null;
        }
        return bool ? 1 : 0;
    }
}
