package com.han.system.sdfz.education;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EducationAcademicYearForms;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 教育学年管理端接口；沿用教育模块既有 /system/education 路由边界。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/academic-years")
@RequiredArgsConstructor
public class EducationAcademicYearController {

    private final EducationAcademicYearService service;

    @GetMapping("/list")
    @PreAuthorize(EducationPermissions.HAS_ACADEMIC_YEAR_LIST)
    public R<PageResult<EduAcademicYearPo>> list(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.list(schoolId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize(EducationPermissions.HAS_ACADEMIC_YEAR_ADD)
    @OperLog(module = "学年管理", type = OperLog.OperType.INSERT)
    public R<Long> add(@Valid @RequestBody EducationAcademicYearForms.AcademicYear form) {
        requireCreate(form.id());
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize(EducationPermissions.HAS_ACADEMIC_YEAR_EDIT)
    @OperLog(module = "学年管理", type = OperLog.OperType.UPDATE)
    public R<Long> edit(@Valid @RequestBody EducationAcademicYearForms.AcademicYear form) {
        requireEdit(form.id());
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize(EducationPermissions.HAS_ACADEMIC_YEAR_REMOVE)
    @OperLog(module = "学年管理", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationAcademicYearForms.DeleteRequest request) {
        return R.ok(service.delete(request.ids()));
    }

    private static void requireCreate(Long id) {
        if (id != null) {
            throw new BusinessException("新增请求不能携带 ID");
        }
    }

    private static void requireEdit(Long id) {
        if (id == null) {
            throw new BusinessException("修改请求必须携带 ID");
        }
    }
}
