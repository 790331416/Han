package com.han.system.sdfz.order.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 订购单管理端可写字段。状态、生效/失效时间、台账全部由服务端推导，不接受前端直接写。
 */
public final class CourseOrderForms {

    private CourseOrderForms() {
    }

    public record CreateOrder(
            /** 可选。带了就是幂等键：同号重复提交返回原单而不是报错。 */
            @Size(max = 64) String orderNo,
            @NotNull Long listenClassId,
            /** 听讲教室，缺省则听课记录的场所留空。 */
            Long listenRoomId,
            /** 听讲端设备，缺省则听课记录的成员留空，听讲端将无法通过设备编码加入课堂。 */
            Long listenDeviceId,
            @NotNull Long lectureClassId,
            @NotNull Long semesterId,
            @NotBlank @Size(max = 16) String grantScope,
            /** BY_SUBJECT 时必填且至少一项；WHOLE_CLASS 时必须为空。 */
            List<Long> subjectIds,
            /**
             * 存草稿而不是直接提交。草稿不占用「一对班级一学期只能有一张有效单」的槽位，
             * 冲突要到提交时才由数据库拦下。缺省 false，直接按当前时间落在 PENDING 或 ACTIVE。
             */
            Boolean draft,
            @Size(max = 500) String remark) {
    }

    public record UpdateScope(
            @NotNull Long id,
            @NotBlank @Size(max = 16) String grantScope,
            List<Long> subjectIds) {
    }

    public record OrderAction(
            @NotNull Long id,
            @Size(max = 200) String reason) {
    }
}
