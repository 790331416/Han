package com.han.job.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.job.domain.entity.SysJob;
import org.apache.ibatis.annotations.Mapper;

/**
 * 定时任务 Mapper
 */
@Mapper
public interface SysJobMapper extends BaseMapper<SysJob> {
}
