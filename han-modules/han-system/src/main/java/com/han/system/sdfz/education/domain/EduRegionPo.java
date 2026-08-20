package com.han.system.sdfz.education.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.BizEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 区域树；只承载区域归属和区域级授权，不承载学校业务数据。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("edu_region")
public class EduRegionPo extends BizEntity {
    private Long parentId;
    private String ancestors;
    private Integer nodeLevel;
    private String regionCode;
    private String regionName;
    private String regionLevel;
    /** NATIONAL 为全国基准数据，HAN 为本地补充区域。 */
    private String sourceSystem;
    private Integer sort;
    private Integer status;
}
