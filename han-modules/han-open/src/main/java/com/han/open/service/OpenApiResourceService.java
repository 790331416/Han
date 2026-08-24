package com.han.open.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;

/**
 * 开放接口资源服务
 */
public interface OpenApiResourceService extends IService<OpenApiResourcePo> {

    /**
     * 查询资源详情，包含版本信息
     * @param id 资源ID
     * @return 资源详情
     */
    OpenApiResourceDetailVO getDetail(Long id);

    /**
     * 校验OpenAPI Schema合法性
     * @param openapiJson OpenAPI JSON字符串
     * @return 是否合法
     */
    boolean validateOpenApiSchema(String openapiJson);

    /**
     * 统一控制资源上线/下线；上线必须已有已发布版本。
     */
    void setOnlineStatus(Long resourceId, boolean online);

    /**
     * 新增资源草稿版本。
     */
    OpenApiResourceVersionVO createDraftVersion(Long resourceId, OpenApiResourceVersionVO version);

    /**
     * 编辑草稿版本；已发布版本正文不可修改。
     */
    OpenApiResourceVersionVO updateDraftVersion(Long versionId, OpenApiResourceVersionVO version);

    /**
     * 发布版本。一个资源同时最多保留一个已发布版本。
     */
    OpenApiResourceVersionVO publishVersion(Long versionId);

    /**
     * 废弃版本，保留版本历史正文。
     */
    OpenApiResourceVersionVO deprecateVersion(Long versionId);
}
