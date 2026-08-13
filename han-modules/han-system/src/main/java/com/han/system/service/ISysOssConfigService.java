package com.han.system.service;

import com.han.common.core.domain.PageResult;
import com.han.system.domain.po.SysOssConfigPo;

/**
 * 对象存储配置服务。
 */
public interface ISysOssConfigService {

    PageResult<SysOssConfigPo> selectPage(Integer pageNum, Integer pageSize, String configKey, String status);

    SysOssConfigPo selectById(Long ossConfigId);

    SysOssConfigPo selectActiveConfig();

    void insert(SysOssConfigPo config);

    void update(SysOssConfigPo config);

    void deleteById(Long ossConfigId);

    void changeStatus(Long ossConfigId);
}
