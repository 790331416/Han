package com.han.open.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OpenAppCredentialVO;

/**
 * 开放平台应用服务接口
 */
public interface IOpenAppService extends IBaseService<OpenAppQuery, OpenAppDTO> {

    /**
     * 分页查询应用列表
     */
    PageResult<OpenAppVO> selectPage(OpenAppQuery query);

    /**
     * 根据 ID 查询详情视图
     */
    OpenAppVO selectVoById(Long appId);

    /** 创建应用并仅返回一次 Client ID/Client Secret。 */
    OpenAppCredentialVO createWithCredentials(OpenAppDTO dto);

    /**
     * 根据AppKey查询应用
     */
    OpenAppVO getAppByAppKey(String appKey);

    /**
     * 重置应用密钥
     */
    String resetAppSecret(Long appId);

    /**
     * 修改应用状态
     */
    void updateStatus(Long appId, Integer status);

    /**
     * 验证客户端凭证
     */
    boolean validateClient(String clientId, String clientSecret);

    /**
     * 验证重定向URI
     */
    boolean validateRedirectUri(String clientId, String redirectUri);
}
