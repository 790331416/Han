package com.xuman.job.convert;

import com.xuman.job.domain.dto.JobDTO;
import com.xuman.job.domain.entity.SysJob;
import com.xuman.job.domain.vo.JobVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.util.List;

/**
 * 定时任务对象转换器
 */
@Mapper(componentModel = "spring")
public interface SysJobConvert {

    /**
     * Entity -> VO
     */
    JobVO toVO(SysJob entity);

    /**
     * Entity List -> VO List
     */
    List<JobVO> toVOList(List<SysJob> entities);

    /**
     * DTO -> Entity
     */
    SysJob toEntity(JobDTO dto);

    /**
     * 更新实体（将 DTO 属性复制到现有 Entity）
     */
    void updateEntity(JobDTO dto, @MappingTarget SysJob entity);
}
