package com.han.open.converter;

import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.po.OpenApiResourceVersionPo;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;

import java.util.List;

/**
 * 开放接口目录对象转换器。
 *
 * <p>这里只映射明确允许对外展示的字段，避免把 PO 的非持久化字段或未来新增字段
 * 意外透传到管理端响应。</p>
 */
public final class OpenApiResourceConverter {

    private OpenApiResourceConverter() {
    }

    public static OpenApiResourceDetailVO toDetailVO(OpenApiResourcePo source, List<OpenApiResourceVersionVO> versions) {
        if (source == null) {
            return null;
        }
        OpenApiResourceDetailVO target = new OpenApiResourceDetailVO();
        target.setId(source.getId());
        target.setResourceCode(source.getResourceCode());
        target.setResourceName(source.getResourceName());
        target.setCategory(source.getCategory());
        target.setHttpMethod(source.getHttpMethod());
        target.setPath(source.getPath());
        target.setScopeCode(source.getScopeCode());
        target.setDescription(source.getDescription());
        target.setSensitivity(source.getSensitivity());
        target.setStatus(source.getStatus());
        target.setPublishStatus(source.getPublishStatus());
        target.setAllowApply(source.getAllowApply());
        target.setAllowTest(source.getAllowTest());
        target.setOwner(source.getOwner());
        target.setSort(source.getSort());
        target.setVersions(versions);
        return target;
    }

    public static OpenApiResourceVersionVO toVersionVO(OpenApiResourceVersionPo source) {
        if (source == null) {
            return null;
        }
        OpenApiResourceVersionVO target = new OpenApiResourceVersionVO();
        target.setId(source.getId());
        target.setResourceId(source.getResourceId());
        target.setVersion(source.getVersion());
        target.setStatus(source.getStatus());
        target.setPublishedAt(source.getPublishedAt());
        target.setDeprecatedAt(source.getDeprecatedAt());
        return target;
    }
}
