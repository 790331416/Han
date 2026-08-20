package com.han.open.converter;

import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.po.OpenAppPo;
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
public interface OpenAppConverter {

    /**
     * PO -> VO
     */
    @Mapping(source = "id", target = "appId")
    @Mapping(source = "redirectUris", target = "redirectUris", qualifiedByName = "stringToList")
    @Mapping(source = "scopes", target = "scopes", qualifiedByName = "stringToList")
    @Mapping(source = "schoolScope", target = "schoolIds", qualifiedByName = "stringToLongList")
    @Mapping(source = "grantTypes", target = "grantTypes", qualifiedByName = "stringToList")
    OpenAppVO toVO(OpenAppPo po);

    /**
     * PO List -> VO List
     */
    List<OpenAppVO> toVOList(List<OpenAppPo> pos);

    /**
     * DTO -> PO
     */
    default OpenAppPo toPo(OpenAppDTO dto) {
        if (dto == null || dto.getBase() == null) {
            return null;
        }
        OpenAppPo po = dto.getBase();
        if (dto.getRedirectUris() != null) {
            po.setRedirectUris(listToString(dto.getRedirectUris()));
        }
        if (dto.getScopes() != null) {
            po.setScopes(listToString(dto.getScopes()));
        }
        if (dto.getSchoolIds() != null) {
            po.setSchoolScope(longListToString(dto.getSchoolIds()));
        }
        if (dto.getGrantTypes() != null) {
            po.setGrantTypes(listToString(dto.getGrantTypes()));
        }
        return po;
    }

    /**
     * 更新实体
     */
    default void updatePo(OpenAppDTO dto, OpenAppPo po) {
        if (dto == null || dto.getBase() == null || po == null) {
            return;
        }
        OpenAppPo base = dto.getBase();
        if (base.getAppName() != null) po.setAppName(base.getAppName());
        if (base.getAppIcon() != null) po.setAppIcon(base.getAppIcon());
        if (base.getAppDesc() != null) po.setAppDesc(base.getAppDesc());
        if (base.getAppType() != null) po.setAppType(base.getAppType());
        if (base.getLogoutUri() != null) po.setLogoutUri(base.getLogoutUri());
        if (base.getAccessTokenTtl() != null) po.setAccessTokenTtl(base.getAccessTokenTtl());
        if (base.getRefreshTokenTtl() != null) po.setRefreshTokenTtl(base.getRefreshTokenTtl());
        if (base.getRequirePkce() != null) po.setRequirePkce(base.getRequirePkce());
        if (base.getAutoApprove() != null) po.setAutoApprove(base.getAutoApprove());
        if (base.getStatus() != null) po.setStatus(base.getStatus());
        if (base.getContactName() != null) po.setContactName(base.getContactName());
        if (base.getContactPhone() != null) po.setContactPhone(base.getContactPhone());
        if (base.getContactEmail() != null) po.setContactEmail(base.getContactEmail());

        if (dto.getRedirectUris() != null) {
            po.setRedirectUris(listToString(dto.getRedirectUris()));
        }
        if (dto.getScopes() != null) {
            po.setScopes(listToString(dto.getScopes()));
        }
        if (dto.getSchoolIds() != null) {
            po.setSchoolScope(longListToString(dto.getSchoolIds()));
        }
        if (dto.getGrantTypes() != null) {
            po.setGrantTypes(listToString(dto.getGrantTypes()));
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

    @Named("stringToLongList")
    default List<Long> stringToLongList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(Long::valueOf)
                .distinct()
                .toList();
    }

    default String longListToString(List<Long> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream().filter(java.util.Objects::nonNull).distinct()
                .map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
    }
}
