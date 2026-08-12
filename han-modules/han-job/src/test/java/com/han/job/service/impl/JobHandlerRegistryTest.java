package com.han.job.service.impl;

import com.han.common.core.exception.BusinessException;
import com.han.job.annotation.JobHandler;
import com.han.job.annotation.JobHandlerMethod;
import com.han.job.mapper.SysJobMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 调用目标白名单校验测试（工单 S-57）。
 */
class JobHandlerRegistryTest {

    private AnnotationConfigApplicationContext context;
    private JobHandlerRegistry registry;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean("demoTask", DemoTaskHandler.class);
        context.registerBean("plainBean", NotAHandlerBean.class);
        context.refresh();

        SysJobMapper jobMapper = mock(SysJobMapper.class);
        when(jobMapper.selectList(any())).thenReturn(List.of());

        registry = new JobHandlerRegistry(context, jobMapper);
        ReflectionTestUtils.setField(registry, "serviceName", "han-job");
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void registeredNoArgTargetIsResolved() throws Exception {
        JobHandlerRegistry.JobInvocation invocation = registry.resolve("demoTask.execute");

        assertThat(invocation.param()).isNull();
        assertThat(invocation.method().getName()).isEqualTo("execute");
        invocation.invoke();
        assertThat(context.getBean(DemoTaskHandler.class).executed).isTrue();
    }

    @Test
    void registeredTargetWithParamKeepsRawArgument() throws Exception {
        JobHandlerRegistry.JobInvocation invocation = registry.resolve("demoTask.executeWithParam(100000,5)");

        assertThat(invocation.param()).isEqualTo("100000,5");
        invocation.invoke();
        assertThat(context.getBean(DemoTaskHandler.class).receivedParam).isEqualTo("100000,5");
    }

    @Test
    void emptyParenthesesAreTreatedAsNoArgCall() {
        JobHandlerRegistry.JobInvocation invocation = registry.resolve("demoTask.execute()");

        assertThat(invocation.param()).isNull();
    }

    @Test
    void arbitraryContainerBeanIsRejected() {
        assertThatThrownBy(() -> registry.resolve("plainBean.dangerous"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void knownExploitTargetsAreRejected() {
        List<String> exploits = List.of(
                "scheduler.shutdown",
                "scheduler.clear",
                "hikariDataSource.close",
                "sysJobLogServiceImpl.cleanJobLog",
                "sysUserServiceImpl.resetPwd(1)");

        for (String exploit : exploits) {
            assertThatThrownBy(() -> registry.resolve(exploit))
                    .as("调用目标 %s 必须被拒绝", exploit)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void unregisteredMethodOnRegisteredHandlerIsRejected() {
        assertThatThrownBy(() -> registry.resolve("demoTask.notExposed"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未注册");
    }

    @Test
    void inheritedObjectMethodIsRejected() {
        assertThatThrownBy(() -> registry.resolve("demoTask.notify"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void argumentArityMustMatchRegisteredSignature() {
        assertThatThrownBy(() -> registry.resolve("demoTask.execute(rm -rf)"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> registry.resolve("demoTask.executeWithParam"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void malformedTargetsAreRejected() {
        List<String> malformed = List.of(
                "demoTask",
                ".execute",
                "demoTask.",
                "demoTask.execute(unclosed",
                "com.han.job.handler.DemoTaskHandler.execute");

        for (String target : malformed) {
            assertThatThrownBy(() -> registry.resolve(target))
                    .as("调用目标 %s 必须被拒绝", target)
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void blankTargetIsRejected() {
        assertThatThrownBy(() -> registry.validateInvokeTarget(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> registry.validateInvokeTarget("   "))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void unsupportedSignatureIsNotExposed() {
        assertThat(registry.listHandlers())
                .extracting("invokeTarget")
                .contains("demoTask.execute", "demoTask.executeWithParam")
                .doesNotContain("demoTask.twoArgs");
    }

    @JobHandler("测试处理器")
    public static class DemoTaskHandler {

        private boolean executed;
        private String receivedParam;

        @JobHandlerMethod("无参方法")
        public void execute() {
            this.executed = true;
        }

        @JobHandlerMethod("带参方法")
        public void executeWithParam(String param) {
            this.receivedParam = param;
        }

        @JobHandlerMethod("签名不受支持")
        public void twoArgs(String first, String second) {
            this.receivedParam = first + second;
        }

        public void notExposed() {
            this.executed = true;
        }
    }

    public static class NotAHandlerBean {

        public void dangerous() {
            throw new IllegalStateException("不应被调度器调用");
        }
    }
}
