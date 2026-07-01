package com.han.common.security.aspect;

import com.han.common.core.constant.CacheConstants;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.XuJsonUtil;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.security.context.SecurityContextHolder;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * 防重复提交切面（Redis 请求指纹 + TTL 锁）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RepeatSubmitAspect {

    private final StringRedisTemplate redisTemplate;

    @Before("@annotation(repeatSubmit)")
    public void doBefore(JoinPoint point, RepeatSubmit repeatSubmit) {
        String key = buildKey(point);
        Boolean absent = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", Duration.ofSeconds(repeatSubmit.interval()));
        if (Boolean.FALSE.equals(absent)) {
            log.warn("重复提交拦截: key={}", key);
            throw new BusinessException(repeatSubmit.message());
        }
    }

    private String buildKey(JoinPoint point) {
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        String methodKey = method.getDeclaringClass().getName() + ":" + method.getName();

        StringBuilder sb = new StringBuilder(CacheConstants.REPEAT_SUBMIT_KEY);
        sb.append(methodKey);

        // 按用户区分（已登录用户用 userId，未登录用 IP）
        Long userId = SecurityContextHolder.getUserId();
        if (userId != null) {
            sb.append(":u:").append(userId);
        } else {
            sb.append(":ip:").append(getClientIp());
        }
        sb.append(":args:").append(buildArgsFingerprint(point.getArgs()));

        return sb.toString();
    }

    private String buildArgsFingerprint(Object[] args) {
        List<Object> values = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                Object value = normalizeArg(arg);
                if (value != null) {
                    values.add(value);
                }
            }
        }
        String payload;
        try {
            payload = XuJsonUtil.toJsonString(values);
        } catch (RuntimeException exception) {
            payload = String.valueOf(values);
        }
        return sha256(payload == null ? "" : payload);
    }

    private Object normalizeArg(Object arg) {
        if (arg == null || arg instanceof ServletRequest || arg instanceof ServletResponse) {
            return null;
        }
        if (arg instanceof MultipartFile file) {
            return Map.of(
                    "name", String.valueOf(file.getOriginalFilename()),
                    "size", file.getSize()
            );
        }
        if (arg instanceof MultipartFile[] files) {
            List<Object> fileInfos = new ArrayList<>();
            for (MultipartFile file : files) {
                fileInfos.add(normalizeArg(file));
            }
            return fileInfos;
        }
        return arg;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String getClientIp() {
        try {
            var attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String ip = request.getHeader("X-Forwarded-For");
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getHeader("X-Real-IP");
                }
                if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                    ip = request.getRemoteAddr();
                }
                if (ip != null && ip.contains(",")) {
                    ip = ip.substring(0, ip.indexOf(",")).trim();
                }
                return ip;
            }
        } catch (Exception e) {
            log.debug("获取客户端IP失败", e);
        }
        return "unknown";
    }
}
