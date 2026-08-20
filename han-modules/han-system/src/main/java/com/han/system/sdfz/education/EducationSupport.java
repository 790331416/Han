package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.EduSchoolPo;

/**
 * 教育主数据服务的公共约束。
 */
final class EducationSupport {

    /** 管理端自建数据的来源标识；数字校园同步数据不允许在管理端改写或删除。 */
    static final String LOCAL_SOURCE = "HAN";

    private static final int MAX_PAGE_SIZE = 100;

    private EducationSupport() {
    }

    static Long requireTenant() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文");
        }
        return tenantId;
    }

    static void requireLocalSource(String sourceSystem, String name) {
        if (!LOCAL_SOURCE.equals(sourceSystem)) {
            throw new BusinessException(name + "来自数字校园，请通过同步维护");
        }
    }

    /**
     * 唯一业务编码的提交前查重。
     *
     * <p>数据库唯一索引仍然是最终兜底，这里只是为了给出可识别的冲突提示，
     * 避免调用方只看到"系统繁忙"。</p>
     */
    static void rejectDuplicate(Long existing, String label, String value) {
        if (existing != null && existing > 0) {
            throw new ConflictException(label + "“" + value + "”已存在，请更换后重试");
        }
    }

    static int normalStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 0 或 1");
        }
        return status;
    }

    static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }

    /** 学年、教学组织、场所和设备只能归属实际办学单位。 */
    static boolean isOperationalSchool(EduSchoolPo school) {
        return school != null && "SCHOOL".equals(school.getOrgType())
                && ("CAMPUS".equals(school.getSchoolManageType()) || "INDEPENDENT".equals(school.getSchoolManageType()));
    }

    static <T> PageResult<T> page(BaseMapper<T> mapper, Wrapper<T> query, int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        Page<T> result = mapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }
}
