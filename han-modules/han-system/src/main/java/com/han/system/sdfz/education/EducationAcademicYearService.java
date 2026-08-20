package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ConflictException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.AcademicYearStatus;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduGradePromotionBatchPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationAcademicYearForms;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduGradePromotionBatchMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 租户统一学年管理；不负责学校级升级执行。 */
@Service
@RequiredArgsConstructor
public class EducationAcademicYearService {

    private final EduAcademicYearMapper academicYearMapper;
    private final EduSemesterMapper semesterMapper;
    private final EduClassMapper classMapper;
    private final EduGradePromotionBatchMapper promotionBatchMapper;
    private final EduSchoolMapper schoolMapper;
    private final EducationDataScopeService dataScopeService;

    public PageResult<EduAcademicYearPo> list(Long schoolId, String keyword, String status, int pageNum, int pageSize) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (schoolId != null) {
            requireTeachingSchool(schoolId);
        } else if (!scope.all() && scope.schoolIds().isEmpty()) {
            return new PageResult<>(List.of(), 0, Math.max(pageNum, 1), Math.min(Math.max(pageSize, 1), 100));
        }
        AcademicYearStatus expectedStatus = status == null || status.isBlank() ? null : AcademicYearStatus.require(status);
        LambdaQueryWrapper<EduAcademicYearPo> query = new LambdaQueryWrapper<EduAcademicYearPo>()
                .eq(schoolId != null, EduAcademicYearPo::getSchoolId, schoolId)
                .in(schoolId == null && !scope.all(), EduAcademicYearPo::getSchoolId, scope.schoolIds())
                .eq(expectedStatus != null, EduAcademicYearPo::getStatus, expectedStatus == null ? null : expectedStatus.name())
                .and(notBlank(keyword), item -> item.like(EduAcademicYearPo::getYearCode, keyword.trim())
                        .or().like(EduAcademicYearPo::getYearName, keyword.trim()))
                .orderByDesc(EduAcademicYearPo::getBeginDate);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<EduAcademicYearPo> page = academicYearMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(page.getRecords(), page.getTotal(), safePage, safeSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(EducationAcademicYearForms.AcademicYear form) {
        Long tenantId = requireTenant();
        if (form.endDate().isBefore(form.beginDate())) {
            throw new BusinessException("学年结束日期不能早于开始日期");
        }
        requireTeachingSchool(form.schoolId());
        AcademicYearStatus status = AcademicYearStatus.require(form.status());
        EduAcademicYearPo item = form.id() == null ? new EduAcademicYearPo() : requireYear(form.id());
        if (item.getId() != null && item.getSchoolId() != null && !item.getSchoolId().equals(form.schoolId())) {
            throw new BusinessException("已归属学校的学年不允许跨学校调整");
        }
        String yearCode = form.yearCode().trim();
        Long duplicate = academicYearMapper.selectCount(new LambdaQueryWrapper<EduAcademicYearPo>()
                .eq(EduAcademicYearPo::getSchoolId, form.schoolId())
                .eq(EduAcademicYearPo::getYearCode, yearCode)
                .ne(form.id() != null, EduAcademicYearPo::getId, form.id()));
        if (duplicate != null && duplicate > 0) {
            throw new ConflictException("学年编码“" + yearCode + "”已存在");
        }
        if (status == AcademicYearStatus.ACTIVE) {
            Long active = academicYearMapper.selectCount(new LambdaQueryWrapper<EduAcademicYearPo>()
                    .eq(EduAcademicYearPo::getSchoolId, form.schoolId())
                    .eq(EduAcademicYearPo::getStatus, AcademicYearStatus.ACTIVE.name())
                    .ne(form.id() != null, EduAcademicYearPo::getId, form.id()));
            if (active != null && active > 0) {
                throw new ConflictException("当前学校已有启用中的学年，请先关闭或调整该学年");
            }
        }
        item.setSchoolId(form.schoolId());
        item.setYearCode(yearCode);
        item.setYearName(form.yearName().trim());
        item.setBeginDate(form.beginDate());
        item.setEndDate(form.endDate());
        item.setStatus(status.name());
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(tenantId);
            academicYearMapper.insert(item);
        } else {
            academicYearMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduAcademicYearPo year = academicYearMapper.selectById(id);
            if (year == null) {
                continue;
            }
            if (year.getSchoolId() == null) {
                throw new BusinessException("历史全租户学年请先完成学校归属迁移后再删除");
            }
            requireTeachingSchool(year.getSchoolId());
            requireNoReference(id, "学期", semesterMapper.selectCount(new LambdaQueryWrapper<EduSemesterPo>()
                    .eq(EduSemesterPo::getAcademicYearId, id)));
            requireNoReference(id, "教学组织", classMapper.selectCount(new LambdaQueryWrapper<EduClassPo>()
                    .eq(EduClassPo::getAcademicYearId, id)));
            Long batches = promotionBatchMapper.selectCount(new LambdaQueryWrapper<EduGradePromotionBatchPo>()
                    .eq(EduGradePromotionBatchPo::getSourceAcademicYearId, id)
                    .or().eq(EduGradePromotionBatchPo::getTargetAcademicYearId, id));
            requireNoReference(id, "升级批次", batches);
            removed += academicYearMapper.deleteById(id);
        }
        return removed;
    }

    private EduAcademicYearPo requireYear(Long id) {
        EduAcademicYearPo value = id == null ? null : academicYearMapper.selectById(id);
        if (value == null) {
            throw new BusinessException("学年不存在或不在当前租户");
        }
        return value;
    }

    private EduSchoolPo requireTeachingSchool(Long schoolId) {
        EduSchoolPo school = schoolId == null ? null : schoolMapper.selectById(schoolId);
        if (!EducationSupport.isOperationalSchool(school)) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(schoolId);
        return school;
    }

    private static void requireNoReference(Long id, String referenceName, Long count) {
        if (count != null && count > 0) {
            throw new BusinessException("学年仍被" + count + "条" + referenceName + "引用，不能删除");
        }
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的学年");
        }
        return ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
    }

    private static Long requireTenant() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文");
        }
        return tenantId;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }
}
