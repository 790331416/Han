package com.xuman.job.domain.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 任务查询 DTO
 */
@Data
public class JobQueryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务名称
     */
    private String jobName;

    /**
     * 任务组名
     */
    private String jobGroup;

    /**
     * 任务状态(0正常 1暂停)
     */
    private String status;

    /**
     * 调用目标
     */
    private String invokeTarget;

    /**
     * 页码
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 10;
}
