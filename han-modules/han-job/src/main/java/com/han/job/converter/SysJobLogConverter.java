package com.han.job.converter;

import com.han.job.domain.po.SysJobLogPo;
import com.han.job.domain.vo.JobLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志对象转换器
 */
@Mapper(componentModel = "spring")
public interface SysJobLogConverter {

    /**
     * PO -> VO
     */
    @Mapping(target = "costTime", source = ".", qualifiedByName = "calculateCostTime")
    JobLogVO toVO(SysJobLogPo po);

    /**
     * PO List -> VO List
     */
    List<JobLogVO> toVOList(List<SysJobLogPo> pos);

    /**
     * 计算执行耗时(毫秒)
     */
    @Named("calculateCostTime")
    default Long calculateCostTime(SysJobLogPo po) {
        LocalDateTime start = po.getStartTime();
        LocalDateTime stop = po.getStopTime();
        if (start != null && stop != null) {
            return Duration.between(start, stop).toMillis();
        }
        return null;
    }
}
