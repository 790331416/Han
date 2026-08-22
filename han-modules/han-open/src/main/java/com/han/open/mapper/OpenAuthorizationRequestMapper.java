package com.han.open.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 授权申请/变更审批Mapper
 */
@Mapper
public interface OpenAuthorizationRequestMapper extends BaseMapper<OpenAuthorizationRequestPo> {

}
