package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysOssConfigPo;

/**
 * OSS storage configuration service.
 */
public interface ISysOssConfigService {

    PageResult<SysOssConfigPo> selectPage(Integer pageNum, Integer pageSize, String configKey, String status);

    SysOssConfigPo selectById(Long ossConfigId);

    SysOssConfigPo selectActiveConfig();

    void insert(SysOssConfigPo config);

    void update(SysOssConfigPo config);

    void deleteById(Long ossConfigId);

    void changeStatus(Long ossConfigId);

    void testConnection(Long ossConfigId);
}
