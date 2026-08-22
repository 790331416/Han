package com.han.open.domain.query;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.han.common.core.domain.query.BaseQuery;
import com.han.open.domain.po.OpenAppPo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 开放平台应用查询对象（采用组合模式）
 *
 * @author han Team
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OpenAppQuery extends BaseQuery {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 组合OpenApp实体
     */
    @JsonUnwrapped
    private OpenAppPo base;

    /** 应用名称（模糊查询） */
    @Schema(description = "应用名称")
    private String appName;

    /** 应用类型 */
    @Schema(description = "应用类型")
    private String appType;

    /** 状态 */
    @Schema(description = "状态(0正常 1停用)")
    private Integer status;

    /** 生命周期状态：0草稿 1待审核 2沙箱开通 3调测中 4生产待审 5生产开通 6暂停 7撤销。 */
    @Schema(description = "应用生命周期状态")
    private Integer lifecycleStatus;

    /** 厂商 ID；普通厂商用户只能查询自己所属厂商。 */
    @Schema(description = "厂商ID")
    private Long vendorId;
}
