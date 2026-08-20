package com.han.system.sdfz.education.domain;

import com.han.common.core.exception.BusinessException;

import java.util.Locale;

/** 学年生命周期；学校级升级进度由升级批次单独维护。 */
public enum AcademicYearStatus {
    DRAFT,
    ACTIVE,
    CLOSED;

    public static AcademicYearStatus require(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException("学年状态不能为空");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("学年状态只能是 DRAFT、ACTIVE 或 CLOSED");
        }
    }
}
