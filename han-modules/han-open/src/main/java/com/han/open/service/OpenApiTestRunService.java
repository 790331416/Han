package com.han.open.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.po.OpenApiTestRunPo;
import com.han.open.domain.vo.OpenApiTestRunVO;

import java.util.List;

/** 厂商在线调测审计服务。 */
public interface OpenApiTestRunService extends IService<OpenApiTestRunPo> {

    /** 提交一次已经完成的调测结果，服务端负责重建目录方法和路径。 */
    OpenApiTestRunVO add(OpenApiTestRunDTO request);

    /** 查询当前登录用户所属厂商应用的最近调测记录。 */
    List<OpenApiTestRunVO> list(Long appId);
}
