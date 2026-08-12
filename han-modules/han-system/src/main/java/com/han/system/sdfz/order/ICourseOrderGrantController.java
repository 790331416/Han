package com.han.system.sdfz.order;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 新课程物化的事件入口（ORDER-06）。
 *
 * <p>旧三课堂不会主动回调 Han，所以真正兜底的是 {@link CourseOrderScheduler#reconcile()} 的
 * 每日对账；这个端点是<b>低延迟通道</b>：旧系统侧（或运维脚本）建课后推一下，
 * 就不用等到下一轮对账。两条通道走的是同一套幂等逻辑，推重了也不会产生重复听课记录。</p>
 *
 * <p>租户由调用方显式给出：内部接口没有登录态，不指定租户的话租户行拦截会整个失效，
 * 变成跨租户扫描。</p>
 */
@Slf4j
@InnerAuth
@RestController
@RequestMapping("/inner/system/order")
@RequiredArgsConstructor
public class ICourseOrderGrantController {

    private final CourseGrantService grantService;
    private final CourseOrderTenantScope tenantScope;

    @PostMapping("/course-created")
    public R<CourseGrantService.SyncResult> onCourseCreated(
            @RequestParam("tenantId") Long tenantId,
            @RequestParam("courseId") String courseId) {
        CourseGrantService.SyncResult[] holder = {new CourseGrantService.SyncResult(0, 0, 0, 0)};
        tenantScope.runAs(tenantId, ignored -> holder[0] = grantService.onCourseCreated(courseId));
        log.info("新课程自动物化: tenantId={}, courseId={}, {}", tenantId, courseId, holder[0]);
        return R.ok(holder[0]);
    }
}
