package com.han.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.system.domain.po.SysNoticeReadPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 通知已读状态 Mapper。
 */
@Mapper
public interface SysNoticeReadMapper extends BaseMapper<SysNoticeReadPo> {
}
