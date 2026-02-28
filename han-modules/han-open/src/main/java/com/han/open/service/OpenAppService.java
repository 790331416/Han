package com.han.open.service;

import com.han.common.core.domain.PageResult;
import com.han.common.web.service.IBaseService;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;

import java.util.List;

/**
 * 开放平台应用服务接口
 * 
 * @author han Team
 */
public interface OpenAppService extends IBaseService<OpenAppQuery, OpenAppDTO> {

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
