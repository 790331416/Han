package com.han.system.sdfz.order;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.SemesterLifecycle;
import com.han.system.sdfz.order.domain.CourseOrderForms;
import com.han.system.sdfz.order.support.OrderIntegrationTestConfig;
import com.han.system.sdfz.order.support.OrderTestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发下的唯一性与幂等。
 *
 * <p>「同一听讲班 + 主讲班 + 学期只能有一张有效单」这条规则的真正执行者是数据库上的
 * {@code uq_edu_course_order_active}，服务里的预检只是为了给出可读错误。这一点只有并发跑真库才能验证：
 * 单线程测试永远走不到预检漏掉、约束兜住的那条路径。</p>
 */
@SpringJUnitConfig(OrderIntegrationTestConfig.class)
@DisplayName("订购单并发唯一性（真实数据库约束）")
class CourseOrderConcurrencyIntegrationTest {

    private static final long SEMESTER = 6001L;
    private static final int THREADS = 8;

    @Autowired
    private CourseOrderService orderService;
    @Autowired
    private OrderTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.setLoginUser(
                LoginUser.builder().userId(9L).tenantId(OrderTestFixtures.TENANT).build());
        fixtures.reset();
        fixtures.seedMasterData();
        fixtures.seedSemester(SEMESTER, "2026-2027-1",
                LocalDate.now(CourseOrderService.ZONE).minusMonths(1),
                LocalDate.now(CourseOrderService.ZONE).plusMonths(4),
                SemesterLifecycle.IN_PROGRESS.name());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    @DisplayName("ORDER-02 八个线程同时建同一关系，只有一个成功，库里只有一张单")
    void concurrentCreateProducesExactlyOneOrder() throws Exception {
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        runConcurrently(() -> {
            try {
                orderService.createOrder(new CourseOrderForms.CreateOrder(
                        null, OrderTestFixtures.LISTEN_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                        SEMESTER, "WHOLE_CLASS", null, false, null));
                succeeded.incrementAndGet();
            } catch (RuntimeException ignored) {
                rejected.incrementAndGet();
            }
            return null;
        });

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(THREADS - 1);
        assertThat(fixtures.countOrders()).isEqualTo(1);
    }

    @Test
    @DisplayName("ORDER-02 八个线程带同一单号并发提交，最终只有一张单")
    void concurrentCreateWithSameOrderNoIsIdempotent() throws Exception {
        AtomicInteger completed = new AtomicInteger();

        runConcurrently(() -> {
            try {
                orderService.createOrder(new CourseOrderForms.CreateOrder(
                        "ORD-RACE", OrderTestFixtures.LISTEN_CLASS, null, null, OrderTestFixtures.LECTURE_CLASS,
                        SEMESTER, "WHOLE_CLASS", null, false, null));
                completed.incrementAndGet();
            } catch (RuntimeException ignored) {
                // 极窄的竞态窗口里可能有线程既没抢到插入、又还没看到对方提交的行。
                // 这里只断言最终态：库里必须只有一张单。
            }
            return null;
        });

        assertThat(completed.get()).isPositive();
        assertThat(fixtures.countOrders()).isEqualTo(1);
    }

    private void runConcurrently(Callable<Void> task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < THREADS; i++) {
                futures.add(pool.submit(() -> {
                    // SecurityContextHolder 是 ThreadLocal，工作线程要自己带上租户上下文。
                    SecurityContextHolder.setLoginUser(
                            LoginUser.builder().userId(9L).tenantId(OrderTestFixtures.TENANT).build());
                    try {
                        start.await();
                        return task.call();
                    } finally {
                        SecurityContextHolder.clear();
                    }
                }));
            }
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
