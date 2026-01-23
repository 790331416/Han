package com.xuman.open.service;

import com.xuman.common.core.domain.PageResult;
import com.xuman.open.domain.dto.OpenAppDTO;
import com.xuman.open.domain.vo.OpenAppVO;

import java.util.List;

/**
 * 开放平台应用服务接口
 */
public interface OpenAppService {

    /**
     * 分页查询应用列表
     */
    PageResult<OpenAppVO> listApps(String appName, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 根据ID查询应用详情
     */
    OpenAppVO getAppById(Long appId);

    /**
     * 根据AppKey查询应用
     */
    OpenAppVO getAppByAppKey(String appKey);

    /**
     * 创建应用
     */
    OpenAppVO createApp(OpenAppDTO dto);

    /**
     * 更新应用
     */
    void updateApp(OpenAppDTO dto);

    /**
     * 删除应用
     */
    void deleteApp(Long appId);

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
