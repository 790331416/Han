package com.xuman.workflow.convert;

import com.xuman.workflow.domain.vo.ProcessDefinitionVO;
import org.flowable.engine.repository.ProcessDefinition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 流程定义对象转换器
 * <p>
 * 注意：ProcessDefinition 来自 Flowable，部分字段需要单独处理
 */
@Mapper(componentModel = "spring")
public interface ProcessDefinitionConvert {

    /**
     * Flowable ProcessDefinition -> VO
     * 注意：deploymentTime 需要从 Deployment 单独获取
     */
    @Mapping(source = "id", target = "processDefinitionId")
    @Mapping(source = "key", target = "processKey")
    @Mapping(source = "name", target = "processName")
    @Mapping(source = "suspended", target = "suspended")
    @Mapping(target = "deploymentTime", ignore = true)
    ProcessDefinitionVO toVO(ProcessDefinition definition);

    /**
     * 批量转换
     */
    List<ProcessDefinitionVO> toVOList(List<ProcessDefinition> definitions);
}
