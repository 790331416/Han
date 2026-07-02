package com.han.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.job.annotation.JobHandler;
import com.han.job.annotation.JobHandlerMethod;
import com.han.job.domain.po.SysJobPo;
import com.han.job.domain.vo.JobHandlerVO;
import com.han.job.mapper.SysJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务处理器注册表
 * <p>
 * 扫描容器中标注 {@link JobHandler} 的 Bean 及其 {@link JobHandlerMethod} 方法，
 * 提供给管理端「新增任务 - 调用目标方法」下拉；业务模块新增可调度方法时
 * 只需在 Bean 与方法上补注解，无需改动此处。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobHandlerRegistry {

    private final ApplicationContext applicationContext;
    private final SysJobMapper jobMapper;

    @Value("${spring.application.name:han-job}")
    private String serviceName;

    public List<JobHandlerVO> listHandlers() {
        List<JobHandlerVO> handlers = new ArrayList<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(JobHandler.class);
        Set<String> configuredTargets = loadConfiguredTargets();

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            Class<?> targetClass = AopUtils.getTargetClass(entry.getValue());
            JobHandler handlerAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, JobHandler.class);
            String handlerDesc = handlerAnnotation != null && !handlerAnnotation.value().isBlank()
                    ? handlerAnnotation.value()
                    : beanName;

            for (Method method : targetClass.getMethods()) {
                JobHandlerMethod methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, JobHandlerMethod.class);
                if (methodAnnotation == null) {
                    continue;
                }
                String invokeTarget = beanName + "." + method.getName();
                String methodDesc = !methodAnnotation.value().isBlank()
                        ? methodAnnotation.value()
                        : method.getName();
                handlers.add(JobHandlerVO.builder()
                        .beanName(beanName)
                        .methodName(method.getName())
                        .hasParam(method.getParameterCount() > 0)
                        .description(handlerDesc + " - " + methodDesc)
                        .invokeTarget(invokeTarget)
                        .serviceName(serviceName)
                        .configured(configuredTargets.contains(invokeTarget))
                        .build());
            }
        }
        handlers.sort(Comparator.comparing(JobHandlerVO::getInvokeTarget));
        return handlers;
    }

    private Set<String> loadConfiguredTargets() {
        try {
            return jobMapper.selectList(new LambdaQueryWrapper<SysJobPo>()
                            .select(SysJobPo::getInvokeTarget)).stream()
                    .map(SysJobPo::getInvokeTarget)
                    .filter(target -> target != null && !target.isBlank())
                    .map(this::stripParams)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("加载已配置任务调用目标失败", e);
            return Set.of();
        }
    }

    private String stripParams(String invokeTarget) {
        int index = invokeTarget.indexOf('(');
        return index > 0 ? invokeTarget.substring(0, index).trim() : invokeTarget.trim();
    }
}
