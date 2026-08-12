package com.han.ai.config;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流式线程池单测：验证任务不再落到 ForkJoinPool.commonPool、
 * 登录上下文能透传，且线程复用前上下文一定被清理。
 */
class AiStreamExecutorTest {

    private AiStreamExecutor executor;

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.destroy();
        }
        SecurityContextHolder.clear();
    }

    private LoginUser loginUser(Long tenantId, String username) {
        LoginUser user = new LoginUser();
        user.setUserId(1001L);
        user.setTenantId(tenantId);
        user.setUsername(username);
        return user;
    }

    @Test
    void runsOnDedicatedPoolAndPropagatesLoginContext() throws Exception {
        executor = new AiStreamExecutor(1, 4, 5);
        SecurityContextHolder.setLoginUser(loginUser(77L, "tenant-user"));

        AtomicReference<String> threadName = new AtomicReference<>();
        AtomicReference<Long> seenTenantId = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);

        executor.execute(() -> {
            threadName.set(Thread.currentThread().getName());
            seenTenantId.set(SecurityContextHolder.getTenantId());
            done.countDown();
        });

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(threadName.get()).startsWith("ai-stream-");
        assertThat(threadName.get()).doesNotContain("ForkJoinPool.commonPool");
        assertThat(seenTenantId.get()).isEqualTo(77L);
    }

    @Test
    void clearsContextSoReusedThreadDoesNotInheritPreviousTenant() throws Exception {
        // 并发度 1 强制两个任务复用同一条线程，暴露上下文污染
        executor = new AiStreamExecutor(1, 4, 5);

        SecurityContextHolder.setLoginUser(loginUser(77L, "first-tenant"));
        CountDownLatch first = new CountDownLatch(1);
        executor.execute(first::countDown);
        assertThat(first.await(5, TimeUnit.SECONDS)).isTrue();

        SecurityContextHolder.clear();
        AtomicReference<Long> leakedTenantId = new AtomicReference<>(-1L);
        CountDownLatch second = new CountDownLatch(1);
        executor.execute(() -> {
            leakedTenantId.set(SecurityContextHolder.getTenantId());
            second.countDown();
        });

        assertThat(second.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(leakedTenantId.get()).isNull();
    }

    @Test
    void failingTaskStillClearsContext() throws Exception {
        executor = new AiStreamExecutor(1, 4, 5);

        SecurityContextHolder.setLoginUser(loginUser(88L, "boom-tenant"));
        CountDownLatch failed = new CountDownLatch(1);
        executor.execute(() -> {
            try {
                throw new IllegalStateException("boom");
            } finally {
                failed.countDown();
            }
        });
        assertThat(failed.await(5, TimeUnit.SECONDS)).isTrue();

        SecurityContextHolder.clear();
        AtomicReference<Long> leakedTenantId = new AtomicReference<>(-1L);
        CountDownLatch next = new CountDownLatch(1);
        executor.execute(() -> {
            leakedTenantId.set(SecurityContextHolder.getTenantId());
            next.countDown();
        });

        assertThat(next.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(leakedTenantId.get()).isNull();
    }
}
