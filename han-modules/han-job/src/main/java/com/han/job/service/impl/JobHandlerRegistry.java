package com.han.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务处理器注册表
 * <p>
 * 扫描容器中标注 {@link JobHandler} 的 Bean 及其 {@link JobHandlerMethod} 方法，
 * 既提供给管理端「新增任务 - 调用目标方法」下拉，也作为调用目标的唯一白名单：
 * 保存任务与执行任务都必须经过 {@link #resolve(String)} 解析，未注册的
 * Bean / 方法一律拒绝，避免通过 invoke_target 反射调用容器内任意 Bean。
 * 业务模块新增可调度方法时只需在 Bean 与方法上补注解，无需改动此处。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobHandlerRegistry {

    private final ApplicationContext applicationContext;
    private final SysJobMapper jobMapper;

    @Value("${spring.application.name:han-job}")
    private String serviceName;

    /**
     * 候选处理器缓存。Bean 定义在容器刷新后不再变化，首次使用时扫描一次即可。
     */
    private volatile Map<String, HandlerCandidate> candidateCache;

    public List<JobHandlerVO> listHandlers() {
        Set<String> configuredTargets = loadConfiguredTargets();
        List<JobHandlerVO> handlers = new ArrayList<>();
        for (HandlerCandidate candidate : candidates().values()) {
            handlers.add(JobHandlerVO.builder()
                    .beanName(candidate.beanName())
                    .methodName(candidate.methodName())
                    .hasParam(candidate.hasParam())
                    .description(candidate.description())
                    .invokeTarget(candidate.invokeTarget())
                    .serviceName(serviceName)
                    .configured(configuredTargets.contains(candidate.invokeTarget()))
                    .build());
        }
        handlers.sort(Comparator.comparing(JobHandlerVO::getInvokeTarget));
        return handlers;
    }

    /**
     * 校验调用目标是否落在已注册的处理器白名单内。
     *
     * @param invokeTarget 调用目标，形如 {@code beanName.methodName} 或 {@code beanName.methodName(参数)}
     * @throws BusinessException 格式非法或未注册时抛出
     */
    public void validateInvokeTarget(String invokeTarget) {
        resolve(invokeTarget);
    }

    /**
     * 解析并校验调用目标，返回可直接反射调用的处理器。
     *
     * <p>解析逻辑集中在此处，保证「保存时校验」与「执行时校验」用的是同一套规则，
     * 不会因为两处解析差异被绕过。</p>
     *
     * @param invokeTarget 调用目标
     * @return 已完成白名单校验的调用描述
     * @throws BusinessException 格式非法或未注册时抛出
     */
    public JobInvocation resolve(String invokeTarget) {
        ParsedTarget parsed = parse(invokeTarget);
        HandlerCandidate candidate = candidates().get(parsed.cacheKey());
        if (candidate == null) {
            throw new BusinessException("调用目标未注册为定时任务处理器，已拒绝执行: " + invokeTarget);
        }
        Object bean = applicationContext.getBean(candidate.beanName());
        return new JobInvocation(bean, candidate.method(), parsed.param());
    }

    private Map<String, HandlerCandidate> candidates() {
        Map<String, HandlerCandidate> local = this.candidateCache;
        if (local == null) {
            synchronized (this) {
                local = this.candidateCache;
                if (local == null) {
                    local = scanCandidates();
                    this.candidateCache = local;
                }
            }
        }
        return local;
    }

    private Map<String, HandlerCandidate> scanCandidates() {
        Map<String, HandlerCandidate> result = new LinkedHashMap<>();
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(JobHandler.class);

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            if (beanName.indexOf('.') >= 0) {
                log.warn("任务处理器 Bean 名含有点号，无法作为调用目标解析，已跳过: {}", beanName);
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(entry.getValue());
            JobHandler handlerAnnotation = AnnotatedElementUtils.findMergedAnnotation(targetClass, JobHandler.class);
            String handlerDesc = handlerAnnotation != null && !handlerAnnotation.value().isBlank()
                    ? handlerAnnotation.value()
                    : beanName;

            for (Method method : targetClass.getMethods()) {
                if (method.isBridge() || method.isSynthetic()) {
                    continue;
                }
                JobHandlerMethod methodAnnotation = AnnotatedElementUtils.findMergedAnnotation(method, JobHandlerMethod.class);
                if (methodAnnotation == null) {
                    continue;
                }
                if (!isSupportedSignature(method)) {
                    log.warn("任务处理方法签名不受支持（仅支持无参或单个 String 参数），已跳过: {}#{}",
                            targetClass.getName(), method.getName());
                    continue;
                }

                boolean hasParam = method.getParameterCount() == 1;
                String methodDesc = !methodAnnotation.value().isBlank()
                        ? methodAnnotation.value()
                        : method.getName();
                HandlerCandidate candidate = new HandlerCandidate(beanName, method.getName(), method, hasParam,
                        handlerDesc + " - " + methodDesc);
                HandlerCandidate exists = result.putIfAbsent(candidate.cacheKey(), candidate);
                if (exists != null) {
                    log.warn("任务处理方法重复注册，保留先注册的: {}", candidate.cacheKey());
                }
            }
        }

        log.info("已注册可调度任务处理方法 {} 个", result.size());
        return Collections.unmodifiableMap(result);
    }

    private boolean isSupportedSignature(Method method) {
        int count = method.getParameterCount();
        return count == 0 || (count == 1 && method.getParameterTypes()[0] == String.class);
    }

    private ParsedTarget parse(String invokeTarget) {
        if (invokeTarget == null || invokeTarget.isBlank()) {
            throw new BusinessException("调用目标不能为空");
        }

        String target = invokeTarget.trim();
        String param = null;
        int paramIndex = target.indexOf('(');
        if (paramIndex >= 0) {
            if (!target.endsWith(")")) {
                throw new BusinessException("调用目标格式错误，参数括号未闭合: " + invokeTarget);
            }
            param = target.substring(paramIndex + 1, target.length() - 1).trim();
            target = target.substring(0, paramIndex).trim();
        }

        int methodIndex = target.indexOf('.');
        if (methodIndex <= 0 || methodIndex == target.length() - 1) {
            throw new BusinessException("调用目标格式错误，应为 beanName.methodName: " + invokeTarget);
        }

        String beanName = target.substring(0, methodIndex);
        String methodName = target.substring(methodIndex + 1);
        if (methodName.indexOf('.') >= 0) {
            throw new BusinessException("调用目标格式错误，应为 beanName.methodName: " + invokeTarget);
        }

        return new ParsedTarget(beanName, methodName, param == null || param.isEmpty() ? null : param);
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

    private static String cacheKey(String beanName, String methodName, boolean hasParam) {
        return beanName + "." + methodName + (hasParam ? "/1" : "/0");
    }

    /**
     * 已注册的候选处理方法。
     */
    private record HandlerCandidate(String beanName, String methodName, Method method, boolean hasParam,
                                    String description) {

        String invokeTarget() {
            return beanName + "." + methodName;
        }

        String cacheKey() {
            return JobHandlerRegistry.cacheKey(beanName, methodName, hasParam);
        }
    }

    /**
     * 解析后的调用目标。
     */
    private record ParsedTarget(String beanName, String methodName, String param) {

        String cacheKey() {
            return JobHandlerRegistry.cacheKey(beanName, methodName, param != null);
        }
    }

    /**
     * 通过白名单校验的调用描述。
     */
    public record JobInvocation(Object bean, Method method, String param) {

        public Object invoke() throws Exception {
            return param == null ? method.invoke(bean) : method.invoke(bean, param);
        }
    }
}
