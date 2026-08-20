package com.han.system.sdfz.education.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 建筑、楼层和场所树的管理端写入模型。 */
public final class EducationPlaceTreeForms {
    private EducationPlaceTreeForms() { }

    public record Node(
            Long id, @NotNull Long schoolId, Long parentId,
            @NotBlank @Size(max = 128) String roomName,
            @NotBlank @Size(max = 16) String nodeType,
            @Size(max = 128) String aliasName, @Size(max = 32) String roomType,
            Integer capacity, BigDecimal longitude, BigDecimal latitude,
            @NotNull @Min(0) Integer sort,
            @NotNull Integer status, @Size(max = 500) String remark) { }

    /** 楼层按数字范围创建；服务端固定生成“X楼”并将数字写入排序值。 */
    public record FloorRange(
            @NotNull Long schoolId,
            @NotNull Long buildingId,
            @NotNull @Min(1) Integer startNo,
            @NotNull @Min(1) Integer endNo,
            @NotNull Integer status) { }
}
