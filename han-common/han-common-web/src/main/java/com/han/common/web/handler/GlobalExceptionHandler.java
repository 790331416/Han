package com.han.common.web.handler;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ForbiddenException;
import com.han.common.core.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - {}", request.getRequestURI(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 未授权异常
     */
    @ExceptionHandler(UnauthorizedException.class)
    public R<Void> handleUnauthorizedException(UnauthorizedException e, HttpServletRequest request) {
        log.warn("未授权访问: {} - {}", request.getRequestURI(), e.getMessage());
        return R.fail(Constants.UNAUTHORIZED, e.getMessage());
    }

    /**
     * 禁止访问异常
     */
    @ExceptionHandler(ForbiddenException.class)
    public R<Void> handleForbiddenException(ForbiddenException e, HttpServletRequest request) {
        log.warn("禁止访问: {} - {}", request.getRequestURI(), e.getMessage());
        return R.fail(Constants.FORBIDDEN, e.getMessage());
    }

    /**
     * 安全异常
     */
    @ExceptionHandler(com.han.common.core.exception.SecurityException.class)
    public R<Void> handleSecurityException(com.han.common.core.exception.SecurityException e, HttpServletRequest request) {
        log.error("安全异常: {} - {}", request.getRequestURI(), e.getMessage());
        return R.fail(Constants.FORBIDDEN, "非法请求");
    }

    /**
     * 参数校验异常 - @Valid
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return R.fail(message);
    }

    /**
     * 参数校验异常 - @Validated
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return R.fail(message);
    }

    /**
     * 参数校验异常 - @Validated 单个参数
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        return R.fail(message);
    }

    /**
     * 缺少请求参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        return R.fail("缺少参数: " + e.getParameterName());
    }

    /**
     * 参数类型不匹配
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        return R.fail("参数类型错误: " + e.getName());
    }

    /**
     * 请求方法不支持
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public R<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        return R.fail("不支持的请求方法: " + e.getMethod());
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public R<Void> handleNoResourceFoundException(NoResourceFoundException e) {
        return R.fail(404, "资源不存在");
    }

    /**
     * 显式响应状态异常
     */
    @ExceptionHandler(ResponseStatusException.class)
    public R<Void> handleResponseStatusException(ResponseStatusException e, HttpServletRequest request) {
        int status = e.getStatusCode().value();
        String message = e.getReason();
        if (status == 404) {
            message = "资源不存在";
        } else if (message == null || message.isBlank()) {
            message = e.getStatusCode().toString();
        }
        log.warn("响应状态异常: {} - {} {}", request.getRequestURI(), status, message);
        return R.fail(status, message);
    }

    /**
     * 其他异常
     */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: {} - {}", request.getRequestURI(), e.getMessage(), e);
        return R.fail("系统繁忙，请稍后重试");
    }
}
