package com.han.open.convert;

import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.entity.OpenApp;
import com.han.open.domain.vo.OpenAppVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
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
    default OpenApp toEntity(OpenAppDTO dto) {
        if (dto == null || dto.getBase() == null) {
            return null;
        }
        OpenApp entity = dto.getBase();
        // 转换列表字段为逗号分隔字符串
        if (dto.getRedirectUris() != null) {
            entity.setRedirectUris(listToString(dto.getRedirectUris()));
        }
        if (dto.getScopes() != null) {
            entity.setScopes(listToString(dto.getScopes()));
        }
        if (dto.getGrantTypes() != null) {
            entity.setGrantTypes(listToString(dto.getGrantTypes()));
        }
        return entity;
    }

    /**
     * 更新实体
     */
    default void updateEntity(OpenAppDTO dto, OpenApp entity) {
        if (dto == null || dto.getBase() == null || entity == null) {
            return;
        }
        OpenApp base = dto.getBase();
        // 从base对象复制基础字段（跳过id、appKey、appSecret）
        if (base.getAppName() != null) entity.setAppName(base.getAppName());
        if (base.getAppIcon() != null) entity.setAppIcon(base.getAppIcon());
        if (base.getAppDesc() != null) entity.setAppDesc(base.getAppDesc());
        if (base.getAppType() != null) entity.setAppType(base.getAppType());
        if (base.getLogoutUri() != null) entity.setLogoutUri(base.getLogoutUri());
        if (base.getAccessTokenTtl() != null) entity.setAccessTokenTtl(base.getAccessTokenTtl());
        if (base.getRefreshTokenTtl() != null) entity.setRefreshTokenTtl(base.getRefreshTokenTtl());
        if (base.getRequirePkce() != null) entity.setRequirePkce(base.getRequirePkce());
        if (base.getAutoApprove() != null) entity.setAutoApprove(base.getAutoApprove());
        if (base.getStatus() != null) entity.setStatus(base.getStatus());
        if (base.getContactName() != null) entity.setContactName(base.getContactName());
        if (base.getContactPhone() != null) entity.setContactPhone(base.getContactPhone());
        if (base.getContactEmail() != null) entity.setContactEmail(base.getContactEmail());
        
        // 转换列表字段
        if (dto.getRedirectUris() != null) {
            entity.setRedirectUris(listToString(dto.getRedirectUris()));
        }
        if (dto.getScopes() != null) {
            entity.setScopes(listToString(dto.getScopes()));
        }
        if (dto.getGrantTypes() != null) {
            entity.setGrantTypes(listToString(dto.getGrantTypes()));
        }
    }

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
}
