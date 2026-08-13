package com.han.common.log.aspect;

import com.han.common.core.util.HanIpUtil;
import com.han.common.log.annotation.OperLog;
import com.han.common.log.config.OperLogProperties;
import com.han.common.log.domain.OperLogEvent;
import com.han.common.log.service.IOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.concurrent.Executor;

/**
 * 操作日志 AOP 切面
 * <p>
 * 拦截标注 @OperLog 的方法，异步采集日志信息并写入数据库。
 * <p>
 * 参数与响应在落库前会过 {@link OperLogMasker} 做字段级脱敏；
 * 容器里没有 {@link IOperLogService} 实现时降级写本地日志，不静默丢弃。
 */
@Slf4j
@Aspect
public class OperLogAspect {

    /** 请求参数和返回结果最大记录长度 */
    private static final int MAX_LENGTH = 2000;

    private final IOperLogService operLogService;
    private final Executor executor;
    private final OperLogMasker masker;

    public OperLogAspect(IOperLogService operLogService, OperLogProperties properties, Executor executor) {
        this.operLogService = operLogService;
        this.executor = executor;
        this.masker = new OperLogMasker(properties != null ? properties.getMaskFields() : null);
    }

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = null;
        Throwable error = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            error = e;
            throw e;
        } finally {
            long costTime = System.currentTimeMillis() - startTime;
            recordLogAsync(joinPoint, operLog, result, error, costTime);
        }
    }

    private void recordLogAsync(ProceedingJoinPoint jp, OperLog operLog,
                                Object result, Throwable error, long costTime) {
        try {
            // 在当前线程快照上下文（异步前必须获取）
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            String operUrl = "";
            String requestMethod = "";
            String operIp = "";
            Long userId = null;
            Long tenantId = null;
            String operName = "";

            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                operUrl = request.getRequestURI();
                requestMethod = request.getMethod();
                operIp = getIpAddr(request);
                // 从 Header 获取用户信息（Gateway 传递）
                userId = parseLong(request.getHeader("X-User-Id"));
                tenantId = parseLong(request.getHeader("X-Tenant-Id"));
                operName = request.getHeader("X-User-Name");
            }

            // 构建日志参数快照
            String operParam = operLog.saveParams() ? getParams(jp) : null;
            String jsonResult = operLog.saveResult() ? truncate(masker.toJson(result), MAX_LENGTH) : null;
            String errorMsg = error != null ? truncate(error.getMessage(), MAX_LENGTH) : null;

            // 快照完成，异步入库
            final Long fUserId = userId;
            final Long fTenantId = tenantId;
            final String fOperName = operName;
            final String fOperUrl = operUrl;
            final String fRequestMethod = requestMethod;
            final String fOperIp = operIp;

            executor.execute(() -> {
                try {
                    OperLogEvent event = OperLogEvent.builder()
                            .tenantId(fTenantId)
                            .module(operLog.module())
                            .operType(operLog.type().getCode())
                            .operName(fOperName)
                            .operUserId(fUserId)
                            .operUrl(fOperUrl)
                            .operIp(fOperIp)
                            .operLocation(HanIpUtil.getLocation(fOperIp))
                            .requestMethod(fRequestMethod)
                            .operParam(operParam)
                            .jsonResult(jsonResult)
                            .status(error == null ? 0 : 1)
                            .errorMsg(errorMsg)
                            .costTime(costTime)
                            .operTime(LocalDateTime.now())
                            .build();
                    if (operLogService != null) {
                        operLogService.recordOperLog(event);
                    } else {
                        // 没有本地实现时降级到本地日志：可见地降级，好过静默丢弃审计记录
                        log.warn("[OperLog] 无 IOperLogService 实现，审计事件仅记录到本地日志: {}", event);
                    }
                } catch (Exception e) {
                    log.error("异步记录操作日志失败", e);
                }
            });
        } catch (Exception e) {
            log.error("采集操作日志信息失败", e);
        }
    }

    /**
     * 获取方法参数（JSON 格式）
     */
    private String getParams(ProceedingJoinPoint jp) {
        try {
            MethodSignature signature = (MethodSignature) jp.getSignature();
            String[] paramNames = signature.getParameterNames();
            Object[] args = jp.getArgs();
            if (paramNames == null || paramNames.length == 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (int i = 0; i < paramNames.length; i++) {
                if (isNotLoggable(args[i])) {
                    continue;
                }
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"").append(paramNames[i]).append("\": ");
                sb.append(masker.isMaskedName(paramNames[i])
                        ? "\"" + OperLogMasker.MASKED + "\""
                        : masker.toJson(args[i]));
            }
            sb.append("}");
            return truncate(sb.toString(), MAX_LENGTH);
        } catch (Exception e) {
            return "[参数序列化失败]";
        }
    }

    /**
     * 不该进日志的参数类型：文件、流、Servlet 对象。
     * <p>大文件导入原先会被整体 base64 进内存再截断，代价是一次完整的内存拷贝。
     */
    private static boolean isNotLoggable(Object arg) {
        return arg instanceof MultipartFile
                || arg instanceof MultipartFile[]
                || arg instanceof InputStream
                || arg instanceof OutputStream
                || arg instanceof HttpServletRequest
                || arg instanceof HttpServletResponse
                || arg instanceof byte[];
    }

    /**
     * 获取客户端 IP 地址
     */
    private static String getIpAddr(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多代理取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(",")).trim();
        }
        return ip;
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }

    private static Long parseLong(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
