package com.han.system.sdfz.order;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.order.domain.CourseOrderForms;
import com.han.system.sdfz.order.domain.EduCourseOrderGrantPo;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程订购管理端接口。
 */
@AdminAuth
@RestController
@RequestMapping("/system/order")
@RequiredArgsConstructor
public class CourseOrderController {

    private final CourseOrderService service;

    /**
     * 订购单详情，含科目明细。
     */
    public record OrderDetail(EduCourseOrderPo order, List<Long> subjectIds) {
    }

    @GetMapping("/courses/list")
    @PreAuthorize("@ss.hasAuthority('order:course:list')")
    public R<PageResult<EduCourseOrderPo>> orders(
            @RequestParam(required = false) Long listenSchoolId,
            @RequestParam(required = false) Long listenClassId,
            @RequestParam(required = false) Long lectureClassId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listOrders(listenSchoolId, listenClassId, lectureClassId,
                semesterId, status, pageNum, pageSize));
    }

    @GetMapping("/courses/{id}")
    @PreAuthorize("@ss.hasAuthority('order:course:list')")
    public R<OrderDetail> order(@PathVariable Long id) {
        EduCourseOrderPo order = service.requireOrder(id);
        return R.ok(new OrderDetail(order, service.subjectIdsOf(id)));
    }

    @RepeatSubmit
    @PostMapping("/courses")
    @PreAuthorize("@ss.hasAuthority('order:course:add')")
    @OperLog(module = "课程订购", type = OperLog.OperType.INSERT)
    public R<EduCourseOrderPo> create(@Valid @RequestBody CourseOrderForms.CreateOrder form) {
        return R.ok(service.createOrder(form));
    }

    @RepeatSubmit
    @PostMapping("/courses/scope")
    @PreAuthorize("@ss.hasAuthority('order:course:edit')")
    @OperLog(module = "课程订购", type = OperLog.OperType.UPDATE)
    public R<EduCourseOrderPo> updateScope(@Valid @RequestBody CourseOrderForms.UpdateScope form) {
        return R.ok(service.updateScope(form));
    }

    @RepeatSubmit
    @PostMapping("/courses/submit")
    @PreAuthorize("@ss.hasAuthority('order:course:submit')")
    @OperLog(module = "课程订购", type = OperLog.OperType.UPDATE)
    public R<EduCourseOrderPo> submit(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        return R.ok(service.submit(form.id()));
    }

    @RepeatSubmit
    @PostMapping("/courses/freeze")
    @PreAuthorize("@ss.hasAuthority('order:course:freeze')")
    @OperLog(module = "课程订购", type = OperLog.OperType.UPDATE)
    public R<EduCourseOrderPo> freeze(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        return R.ok(service.freezeAndSuspend(form.id(), form.reason()));
    }

    @RepeatSubmit
    @PostMapping("/courses/unfreeze")
    @PreAuthorize("@ss.hasAuthority('order:course:freeze')")
    @OperLog(module = "课程订购", type = OperLog.OperType.UPDATE)
    public R<CourseGrantService.SyncResult> unfreeze(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        return R.ok(service.unfreezeAndResume(form.id()));
    }

    @RepeatSubmit
    @PostMapping("/courses/cancel")
    @PreAuthorize("@ss.hasAuthority('order:course:cancel')")
    @OperLog(module = "课程订购", type = OperLog.OperType.UPDATE)
    public R<EduCourseOrderPo> cancel(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        // 默认口径：未开始的课程撤销，已结束的课程保留回放（《课程订购关系管理说明》§4.4）。
        return R.ok(service.cancelAndRevoke(form.id(), form.reason(), true));
    }

    @RepeatSubmit
    @PostMapping("/courses/sync")
    @PreAuthorize("@ss.hasAuthority('order:course:sync')")
    @OperLog(module = "课程订购", type = OperLog.OperType.OTHER)
    public R<CourseGrantService.SyncResult> sync(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        return R.ok(service.syncGrants(form.id()));
    }

    @GetMapping("/grants/list")
    @PreAuthorize("@ss.hasAuthority('order:grant:list')")
    public R<PageResult<EduCourseOrderGrantPo>> grants(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String grantStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listGrants(orderId, grantStatus, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/grants/retry")
    @PreAuthorize("@ss.hasAuthority('order:grant:retry')")
    @OperLog(module = "课程订购", type = OperLog.OperType.OTHER)
    public R<CourseGrantService.SyncResult> retryGrant(@Valid @RequestBody CourseOrderForms.OrderAction form) {
        return R.ok(service.retryGrant(form.id()));
    }
}
