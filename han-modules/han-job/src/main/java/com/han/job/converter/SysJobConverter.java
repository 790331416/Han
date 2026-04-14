package com.han.job.converter;

import com.han.job.domain.dto.JobDTO;
import com.han.job.domain.po.SysJobPo;
import com.han.job.domain.vo.JobVO;
import org.mapstruct.Mapper;
import java.util.List;

/**
 * 任务对象转换器
 */
@Mapper(componentModel = "spring")
public interface SysJobConverter {

    /**
     * PO -> DTO
     */
    @Mapping(source = ".", target = "base")
    JobDTO toDto(SysJobPo po);

    /**
     * PO List -> DTO List
     */
    List<JobDTO> toDtoList(List<SysJobPo> pos);

    /**
     * PO -> VO
     */
    JobVO toVo(SysJobPo po);

    /**
     * PO List -> VO List
     */
    List<JobVO> toVoList(List<SysJobPo> pos);

    /**
     * DTO -> PO
     */
    @Mapping(source = "base", target = ".")
    SysJobPo toPo(JobDTO dto);
}
