package com.han.system.sdfz.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.system.sdfz.education.EducationCalendarService;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.OrderStatus;
import com.han.system.sdfz.order.mapper.EduCourseOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订购模块的三个后台任务。
 *
 * <p>三条同步通道（事件驱动、定时对账、手动重试）<b>共用同一套幂等 upsert 逻辑</b>
 * （{@link CourseGrantService#syncOrder}），不允许各写各的。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "sdfz.order.schedule", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class CourseOrderScheduler {

    private final EduCourseOrderMapper orderMapper;
    private final CourseOrderService orderService;
    private final CourseGrantService grantService;
    private final EducationCalendarService calendarService;
    private final CourseOrderTenantScope tenantScope;

    @Value("${sdfz.order.retry.max-attempts:6}")
    private int maxAttempts;

    @Value("${sdfz.order.retry.batch-size:200}")
    private int retryBatchSize;

    /**
     * 每小时推进学期阶段与订购单状态。
     *
     * <p>幂等：已经处在正确状态的行不会被更新，重复执行没有副作用。</p>
     */
    @Scheduled(cron = "${sdfz.order.schedule.lifecycle-cron:0 5 * * * ?}")
    public void advanceLifecycle() {
        LocalDateTime now = CourseOrderService.now();
        for (Long tenantId : tenantScope.tenantsWithOrders()) {
            tenantScope.runAs(tenantId, ignored -> {
                int semesters = calendarService.advanceSemesterLifecycle(now.toLocalDate());
                int orders = orderService.advanceStatuses(now);
                if (semesters > 0 || orders > 0) {
                    log.info("订购状态推进完成: tenantId={}, 学期={}, 订购单={}", tenantId, semesters, orders);
                }
            });
        }
    }

    /**
     * 每十分钟重试失败台账（ORDER-09）。指数退避在 {@link CourseGrantService#retryFailed} 里。
     */
    @Scheduled(cron = "${sdfz.order.schedule.retry-cron:0 */10 * * * ?}")
    public void retryFailedGrants() {
        LocalDateTime now = CourseOrderService.now();
        for (Long tenantId : tenantScope.tenantsWithOrders()) {
            tenantScope.runAs(tenantId, ignored -> {
                CourseGrantService.SyncResult result =
                        grantService.retryFailed(maxAttempts, retryBatchSize, now);
                if (result.total() > 0) {
                    log.info("失败授权重试: tenantId={}, {}", tenantId, result);
                }
            });
        }
    }

    /**
     * 每日低峰期全量对账（§6.2），兜底事件丢失与旧系统侧的连带删除。
     *
     * <p>教师在旧前端改课时 {@code deleteByCourseId} 会把整门课的听课行清掉，
     * 连带 Han 写的那行。这条通道就是把它补回来的地方。</p>
     */
    @Scheduled(cron = "${sdfz.order.schedule.reconcile-cron:0 30 3 * * ?}")
    public void reconcile() {
        for (Long tenantId : tenantScope.tenantsWithOrders()) {
            tenantScope.runAs(tenantId, ignored -> {
                List<EduCourseOrderPo> orders = orderMapper.selectList(
                        new LambdaQueryWrapper<EduCourseOrderPo>()
                                .eq(EduCourseOrderPo::getStatus, OrderStatus.ACTIVE.name()));
                int supplemented = 0;
                int withdrawn = 0;
                int repaired = 0;
                for (EduCourseOrderPo order : orders) {
                    try {
                        CourseGrantService.ReconcileResult result = grantService.reconcileOrder(order);
                        supplemented += result.supplemented();
                        withdrawn += result.withdrawn();
                        repaired += result.repaired();
                    } catch (RuntimeException ex) {
                        // 一张单对不上不能拖垮整轮对账。
                        log.warn("订购单对账失败: orderId={}, reason={}",
                                order.getId(), ex.getMessage());
                    }
                }
                if (supplemented + withdrawn + repaired > 0) {
                    log.info("订购授权对账完成: tenantId={}, 补授权={}, 撤销={}, 修复={}",
                            tenantId, supplemented, withdrawn, repaired);
                }
            });
        }
    }
}
