package com.han.system.sdfz.education;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教育主数据管理端接口。
 */
@AdminAuth
@RestController
@RequestMapping("/system/education")
@RequiredArgsConstructor
public class EducationMasterDataController {

    private final EducationMasterDataService service;

    @GetMapping("/schools/list")
    @PreAuthorize("@ss.hasAuthority('education:school:list')")
    public R<PageResult<EduSchoolPo>> schools(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listSchools(keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/schools")
    @PreAuthorize("@ss.hasAuthority('education:school:add')")
    @OperLog(module = "学校管理", type = OperLog.OperType.INSERT)
    public R<Long> addSchool(@Valid @RequestBody EducationForms.School form) {
        requireCreate(form.id());
        return R.ok(service.saveSchool(form));
    }

    @RepeatSubmit
    @PostMapping("/schools/edit")
    @PreAuthorize("@ss.hasAuthority('education:school:edit')")
    @OperLog(module = "学校管理", type = OperLog.OperType.UPDATE)
    public R<Long> editSchool(@Valid @RequestBody EducationForms.School form) {
        requireEdit(form.id());
        return R.ok(service.saveSchool(form));
    }

    @GetMapping("/classes/list")
    @PreAuthorize("@ss.hasAuthority('education:class:list')")
    public R<PageResult<EduClassPo>> classes(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listClasses(schoolId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/classes")
    @PreAuthorize("@ss.hasAuthority('education:class:add')")
    @OperLog(module = "班级管理", type = OperLog.OperType.INSERT)
    public R<Long> addClass(@Valid @RequestBody EducationForms.ClassInfo form) {
        requireCreate(form.id());
        return R.ok(service.saveClass(form));
    }

    @RepeatSubmit
    @PostMapping("/classes/edit")
    @PreAuthorize("@ss.hasAuthority('education:class:edit')")
    @OperLog(module = "班级管理", type = OperLog.OperType.UPDATE)
    public R<Long> editClass(@Valid @RequestBody EducationForms.ClassInfo form) {
        requireEdit(form.id());
        return R.ok(service.saveClass(form));
    }

    @GetMapping("/people/list")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<PageResult<EduPersonPo>> people(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listPeople(schoolId, personType, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/people")
    @PreAuthorize("@ss.hasAuthority('education:person:add')")
    @OperLog(module = "人员管理", type = OperLog.OperType.INSERT)
    public R<Long> addPerson(@Valid @RequestBody EducationForms.Person form) {
        requireCreate(form.id());
        return R.ok(service.savePerson(form));
    }

    @RepeatSubmit
    @PostMapping("/people/edit")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.UPDATE)
    public R<Long> editPerson(@Valid @RequestBody EducationForms.Person form) {
        requireEdit(form.id());
        return R.ok(service.savePerson(form));
    }

    @GetMapping("/subjects/list")
    @PreAuthorize("@ss.hasAuthority('education:subject:list')")
    public R<PageResult<EduSubjectPo>> subjects(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listSubjects(keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/subjects")
    @PreAuthorize("@ss.hasAuthority('education:subject:add')")
    @OperLog(module = "科目管理", type = OperLog.OperType.INSERT)
    public R<Long> addSubject(@Valid @RequestBody EducationForms.Subject form) {
        requireCreate(form.id());
        return R.ok(service.saveSubject(form));
    }

    @RepeatSubmit
    @PostMapping("/subjects/edit")
    @PreAuthorize("@ss.hasAuthority('education:subject:edit')")
    @OperLog(module = "科目管理", type = OperLog.OperType.UPDATE)
    public R<Long> editSubject(@Valid @RequestBody EducationForms.Subject form) {
        requireEdit(form.id());
        return R.ok(service.saveSubject(form));
    }

    @GetMapping("/devices/list")
    @PreAuthorize("@ss.hasAuthority('education:device:list')")
    public R<PageResult<EduDevicePo>> devices(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listDevices(schoolId, roomId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/devices")
    @PreAuthorize("@ss.hasAuthority('education:device:add')")
    @OperLog(module = "设备管理", type = OperLog.OperType.INSERT)
    public R<Long> addDevice(@Valid @RequestBody EducationForms.Device form) {
        requireCreate(form.id());
        return R.ok(service.saveDevice(form));
    }

    @RepeatSubmit
    @PostMapping("/devices/edit")
    @PreAuthorize("@ss.hasAuthority('education:device:edit')")
    @OperLog(module = "设备管理", type = OperLog.OperType.UPDATE)
    public R<Long> editDevice(@Valid @RequestBody EducationForms.Device form) {
        requireEdit(form.id());
        return R.ok(service.saveDevice(form));
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
