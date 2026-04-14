package com.han.job.domain.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.job.domain.po.SysJobPo;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务创建/更新 DTO（采用组合模式）
 * 
 * @author han Team
 */
@Data
public class JobDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonUnwrapped
    private SysJobPo base;

}
