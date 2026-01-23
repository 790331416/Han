package com.xuman.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xuman.job.domain.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
