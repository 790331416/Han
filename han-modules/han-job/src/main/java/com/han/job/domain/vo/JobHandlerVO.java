package com.han.job.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可用任务处理器 VO（管理端「调用目标方法」下拉数据）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobHandlerVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Bean 名称 */
    private String beanName;

    /** 方法名称 */
    private String methodName;

    /** 是否有参数 */
    private boolean hasParam;

    /** 描述 */
    private String description;

    /** 调用目标（beanName.methodName） */
    private String invokeTarget;

    /** 所属服务 */
    private String serviceName;

    /** 是否已被某任务配置使用 */
    private boolean configured;
}
