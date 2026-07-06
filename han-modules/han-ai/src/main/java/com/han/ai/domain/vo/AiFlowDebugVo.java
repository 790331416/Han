package com.han.ai.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 编排调试运行结果（设计器「调试运行」抽屉消费，不落会话消息）。
 */
@Data
public class AiFlowDebugVo {

    /**
     * 是否执行成功
     */
    private Boolean success;

    /**
     * 最终回复文本（失败时为失败说明）
     */
    private String reply;

    /**
     * 节点执行时间线
     */
    private List<AiFlowNodeTraceVo> nodeTraces;
}
