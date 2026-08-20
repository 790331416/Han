package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EducationCourseRuleForms;
import com.han.system.sdfz.order.legacy.LegacyCourseRule;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 课表节次管理；同一套规则供校端预约和课表展示使用。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/course-rules")
@RequiredArgsConstructor
public class EducationCourseRuleController {

    private final EducationCourseRuleService service;

    @GetMapping("/list")
    @PreAuthorize(EducationPermissions.HAS_COURSE_RULE_LIST)
    public R<List<LegacyCourseRule>> list() {
        return R.ok(service.list());
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize(EducationPermissions.HAS_COURSE_RULE_ADD)
    @OperLog(module = "课表节次", type = OperLog.OperType.INSERT)
    public R<String> add(@Valid @RequestBody EducationCourseRuleForms.Rule form) {
        return R.ok(service.add(form));
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize(EducationPermissions.HAS_COURSE_RULE_EDIT)
    @OperLog(module = "课表节次", type = OperLog.OperType.UPDATE)
    public R<Void> edit(@Valid @RequestBody EducationCourseRuleForms.Rule form) {
        service.edit(form);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/status")
    @PreAuthorize(EducationPermissions.HAS_COURSE_RULE_EDIT)
    @OperLog(module = "课表节次", type = OperLog.OperType.UPDATE)
    public R<Void> status(@Valid @RequestBody EducationCourseRuleForms.Status form) {
        service.changeStatus(form);
        return R.ok();
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize(EducationPermissions.HAS_COURSE_RULE_REMOVE)
    @OperLog(module = "课表节次", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationCourseRuleForms.DeleteRequest request) {
        return R.ok(service.deleteRules(request.ids()));
    }
}
