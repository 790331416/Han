package com.han.job.convert;

import com.han.job.domain.entity.SysJobLog;
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
public interface SysJobLogConvert {

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
    default Long calculateCostTime(SysJobLog entity) {
        LocalDateTime start = entity.getStartTime();
        LocalDateTime stop = entity.getStopTime();
        if (start != null && stop != null) {
            return Duration.between(start, stop).toMillis();
        }
        return null;
    }
}
