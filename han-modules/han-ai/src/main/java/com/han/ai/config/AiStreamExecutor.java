package com.han.ai.config;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 流式生成专用线程池。
 * <p>
 * 流式对话、编排调试、内部文本生成三条链路此前都用无 Executor 的
 * {@code CompletableFuture.runAsync}，任务落在 {@code ForkJoinPool.commonPool()} 上执行。
 * commonPool 的并行度是「CPU 核数 - 1」，2 核容器上只有 1 —— 而这些任务是 300 秒级的阻塞 IO，
 * 结果是并发对话互相饿死，并且会连带拖垮同一 JVM 内所有依赖 commonPool 的并行流与异步任务。
 * <p>
 * 本池特性：
 * <ul>
 *   <li>固定并发上限 + 有界队列，超出后快速失败，由调用方按 SSE 协议回传可读错误，
 *       而不是无限堆积任务把内存耗尽；</li>
 *   <li>核心线程允许超时回收，空闲期不长期占用线程；</li>
 *   <li>提交时捕获登录上下文、工作线程执行前设置、执行完毕在 finally 清理，
 *       保证租户与登录身份能透传，且不会污染被复用的线程。</li>
 * </ul>
 */
@Slf4j
@Component
public class AiStreamExecutor implements DisposableBean {

    private final ThreadPoolExecutor delegate;

    public AiStreamExecutor(
            @Value("${han.ai.stream.max-concurrency:32}") int maxConcurrency,
            @Value("${han.ai.stream.queue-capacity:64}") int queueCapacity,
            @Value("${han.ai.stream.keep-alive-seconds:120}") long keepAliveSeconds) {
        int concurrency = Math.max(1, maxConcurrency);
        int queue = Math.max(1, queueCapacity);
        this.delegate = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                Math.max(1L, keepAliveSeconds),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queue),
                namedThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());
        this.delegate.allowCoreThreadTimeOut(true);
        log.info("AI stream executor initialized, concurrency={}, queueCapacity={}", concurrency, queue);
    }

    /**
     * 提交一个流式生成任务，登录上下文随任务透传。
     *
     * @throws RejectedExecutionException 并发与队列都已打满，调用方应向客户端回传可读的繁忙提示
     */
    public void execute(Runnable task) {
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        delegate.execute(() -> {
            if (loginUser != null) {
                SecurityContextHolder.setLoginUser(loginUser);
            }
            try {
                task.run();
            } finally {
                // 线程会被复用，无论成败都必须清理，否则下一个任务会继承上一个租户的身份
                SecurityContextHolder.clear();
            }
        });
    }

    /**
     * 当前排队等待执行的任务数，用于监控与容量评估。
     */
    public int queuedTaskCount() {
        return delegate.getQueue().size();
    }

    @Override
    public void destroy() {
        delegate.shutdown();
    }

    private ThreadFactory namedThreadFactory() {
        AtomicInteger sequence = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, "ai-stream-" + sequence.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
