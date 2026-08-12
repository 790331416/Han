package com.han.system.sdfz.education;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduPersonSubjectPo;
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

import java.util.List;

/**
 * 教育主数据管理端接口。
 *
 * <p>人员写入接口关闭操作日志的参数与响应留痕：请求体可能携带初始口令，响应可能回传服务端生成的初始口令，
 * 两者都不允许落到 {@code sys_oper_log}。</p>
 */
@AdminAuth
@RestController
@RequestMapping("/system/education")
@RequiredArgsConstructor
public class EducationMasterDataController {

    private final EducationMasterDataService service;
    private final EducationPersonService personService;

    // ---------------------------------------------------------------- 学校

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

    @RepeatSubmit
    @PostMapping("/schools/remove")
    @PreAuthorize("@ss.hasAuthority('education:school:remove')")
    @OperLog(module = "学校管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeSchools(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteSchools(request.ids()));
    }

    // ---------------------------------------------------------------- 班级

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

    @RepeatSubmit
    @PostMapping("/classes/remove")
    @PreAuthorize("@ss.hasAuthority('education:class:remove')")
    @OperLog(module = "班级管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeClasses(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteClasses(request.ids()));
    }

    // ---------------------------------------------------------------- 人员

    @GetMapping("/people/list")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<PageResult<EduPersonPo>> people(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(personService.list(schoolId, personType, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/people")
    @PreAuthorize("@ss.hasAuthority('education:person:add')")
    @OperLog(module = "人员管理", type = OperLog.OperType.INSERT, saveParams = false, saveResult = false)
    public R<EducationForms.PersonResult> addPerson(@Valid @RequestBody EducationForms.Person form) {
        requireCreate(form.id());
        return R.ok(personService.save(form));
    }

    @RepeatSubmit
    @PostMapping("/people/edit")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.UPDATE, saveParams = false, saveResult = false)
    public R<EducationForms.PersonResult> editPerson(@Valid @RequestBody EducationForms.Person form) {
        requireEdit(form.id());
        return R.ok(personService.save(form));
    }

    @RepeatSubmit
    @PostMapping("/people/remove")
    @PreAuthorize("@ss.hasAuthority('education:person:remove')")
    @OperLog(module = "人员管理", type = OperLog.OperType.DELETE)
    public R<Integer> removePeople(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(personService.deletePeople(request.ids()));
    }

    @GetMapping("/people/memberships")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<EduPersonClassPo>> memberships(@RequestParam Long personId) {
        return R.ok(personService.listMemberships(personId));
    }

    @RepeatSubmit
    @PostMapping("/people/memberships")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.GRANT)
    public R<Integer> replaceMemberships(@Valid @RequestBody EducationForms.Membership form) {
        return R.ok(personService.replaceMemberships(form));
    }

    @GetMapping("/people/subjects")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<EduPersonSubjectPo>> assignments(@RequestParam Long personId) {
        return R.ok(personService.listAssignments(personId));
    }

    /** 供编辑页回填登录角色，避免前端提交空数组把角色清空。 */
    @GetMapping("/people/roles")
    @PreAuthorize("@ss.hasAuthority('education:person:list')")
    public R<List<Long>> personRoles(@RequestParam Long personId) {
        return R.ok(personService.listRoleIds(personId));
    }

    @RepeatSubmit
    @PostMapping("/people/subjects")
    @PreAuthorize("@ss.hasAuthority('education:person:edit')")
    @OperLog(module = "人员管理", type = OperLog.OperType.GRANT)
    public R<Integer> replaceAssignments(@Valid @RequestBody EducationForms.TeachingAssignment form) {
        return R.ok(personService.replaceAssignments(form));
    }

    // ---------------------------------------------------------------- 科目

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

    @RepeatSubmit
    @PostMapping("/subjects/remove")
    @PreAuthorize("@ss.hasAuthority('education:subject:remove')")
    @OperLog(module = "科目管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeSubjects(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteSubjects(request.ids()));
    }

    // ---------------------------------------------------------------- 设备

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

    @RepeatSubmit
    @PostMapping("/devices/remove")
    @PreAuthorize("@ss.hasAuthority('education:device:remove')")
    @OperLog(module = "设备管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeDevices(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteDevices(request.ids()));
    }

    // 学期与教室不在这里：它们由 EducationCalendarController 独占 /semesters/* 与 /rooms/*。
    // 两边都挂在 @RequestMapping("/system/education") 上，同名端点各写一份会让 Spring 在启动期
    // 抛 Ambiguous mapping，han-system 直接起不来，而 mvn install 阶段看不出来。

    // ---------------------------------------------------------------- 内部

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
