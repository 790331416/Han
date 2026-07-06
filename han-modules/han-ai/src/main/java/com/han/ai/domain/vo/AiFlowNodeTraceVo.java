package com.han.ai.domain.vo;

import lombok.Data;

/**
 * 编排节点执行轨迹（advanced 工作流执行引擎产出，随消息 meta 落库并回传前端时间线）。
 */
@Data
public class AiFlowNodeTraceVo {

    private String nodeId;

    /**
     * 节点类型：start / llm / knowledge / condition / tool / output / end
     */
    private String nodeType;

    private String nodeName;

    /**
     * 执行状态：succeeded / failed / skipped
     */
    private String status;

    /**
     * 输入摘要
     */
    private String input;

    /**
     * 输出摘要
     */
    private String output;

    private Long costMs;

    /**
     * 失败原因（status=failed 时）
     */
    private String error;
}
