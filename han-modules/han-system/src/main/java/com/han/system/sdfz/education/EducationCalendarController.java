package com.han.system.sdfz.education;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EducationCalendarForms;
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
 * 学期与教室管理端接口，补齐 20260811 教育主数据脚本里缺失的两个菜单。
 */
@AdminAuth
@RestController
@RequestMapping("/system/education")
@RequiredArgsConstructor
public class EducationCalendarController {

    private final EducationCalendarService service;

    @GetMapping("/semesters/list")
    @PreAuthorize("@ss.hasAuthority('education:semester:list')")
    public R<PageResult<EduSemesterPo>> semesters(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String lifecycleStatus,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listSemesters(keyword, status, lifecycleStatus, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/semesters")
    @PreAuthorize("@ss.hasAuthority('education:semester:add')")
    @OperLog(module = "学期管理", type = OperLog.OperType.INSERT)
    public R<Long> addSemester(@Valid @RequestBody EducationCalendarForms.Semester form) {
        requireCreate(form.id());
        return R.ok(service.saveSemester(form));
    }

    @RepeatSubmit
    @PostMapping("/semesters/edit")
    @PreAuthorize("@ss.hasAuthority('education:semester:edit')")
    @OperLog(module = "学期管理", type = OperLog.OperType.UPDATE)
    public R<Long> editSemester(@Valid @RequestBody EducationCalendarForms.Semester form) {
        requireEdit(form.id());
        return R.ok(service.saveSemester(form));
    }

    @RepeatSubmit
    @PostMapping("/semesters/remove")
    @PreAuthorize("@ss.hasAuthority('education:semester:remove')")
    @OperLog(module = "学期管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeSemesters(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteSemesters(request.ids()));
    }

    @GetMapping("/rooms/list")
    @PreAuthorize("@ss.hasAuthority('education:room:list')")
    public R<PageResult<EduRoomPo>> rooms(
            @RequestParam(required = false) Long schoolId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return R.ok(service.listRooms(schoolId, keyword, status, pageNum, pageSize));
    }

    @RepeatSubmit
    @PostMapping("/rooms")
    @PreAuthorize("@ss.hasAuthority('education:room:add')")
    @OperLog(module = "教室管理", type = OperLog.OperType.INSERT)
    public R<Long> addRoom(@Valid @RequestBody EducationCalendarForms.Room form) {
        requireCreate(form.id());
        return R.ok(service.saveRoom(form));
    }

    @RepeatSubmit
    @PostMapping("/rooms/edit")
    @PreAuthorize("@ss.hasAuthority('education:room:edit')")
    @OperLog(module = "教室管理", type = OperLog.OperType.UPDATE)
    public R<Long> editRoom(@Valid @RequestBody EducationCalendarForms.Room form) {
        requireEdit(form.id());
        return R.ok(service.saveRoom(form));
    }

    @RepeatSubmit
    @PostMapping("/rooms/remove")
    @PreAuthorize("@ss.hasAuthority('education:room:remove')")
    @OperLog(module = "教室管理", type = OperLog.OperType.DELETE)
    public R<Integer> removeRooms(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.deleteRooms(request.ids()));
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
