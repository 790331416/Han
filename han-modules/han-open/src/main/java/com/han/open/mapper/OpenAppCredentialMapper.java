package com.han.open.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.han.open.domain.po.OpenAppCredentialPo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 应用分环境凭证Mapper
 */
@Mapper
public interface OpenAppCredentialMapper extends BaseMapper<OpenAppCredentialPo> {

}
