package com.xuman.job.convert;

import com.xuman.job.domain.entity.SysJobLog;
import com.xuman.job.domain.vo.JobLogVO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务日志对象转换器
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface SysJobLogConverter {

    /**
     * Entity -> VO
     */
    @Mapping(target = "costTime", source = ".", qualifiedByName = "calculateCostTime")
    JobLogVO toVO(SysJobLog entity);

    /**
     * Entity List -> VO List
     */
    List<JobLogVO> toVOList(List<SysJobLog> entities);

    /**
     * 计算执行耗时(毫秒)
     */
    @Named("calculateCostTime")
    default Long calculateCostTime(SysJobLog log) {
        LocalDateTime start = log.getStartTime();
        LocalDateTime stop = log.getStopTime();
        if (start != null && stop != null) {
            return Duration.between(start, stop).toMillis();
        }
        return null;
    }
}
public class SysJobLogConverter {
    
}
