package com.han.open.service;

import com.han.open.domain.po.OpenApiResourcePo;

/**
 * 资源路径映射服务
 * 将请求的HTTP方法和路径映射到对应的API资源
 */
public interface ResourcePathMappingService {

    /**
     * 根据请求方法和路径匹配对应的资源
     * @param httpMethod HTTP方法（GET/POST/PUT/DELETE等）
     * @param path 请求路径
     * @return 匹配的资源，未匹配返回null
     */
    OpenApiResourcePo matchResource(String httpMethod, String path);

    /**
     * 刷新资源映射缓存
     */
    void refreshCache();
}
