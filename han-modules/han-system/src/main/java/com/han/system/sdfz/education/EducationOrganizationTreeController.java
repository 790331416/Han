package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EducationOrganizationForms;
import com.han.system.sdfz.education.domain.EducationOrganizationNode;
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

/** 教育组织树管理端接口；学校、教育局和校区共用同一棵树。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/organizations")
@RequiredArgsConstructor
public class EducationOrganizationTreeController {

    private static final String SCHOOL_LIST = "@ss.hasAuthority('education:school:list')";
    private static final String SCHOOL_ADD = "@ss.hasAuthority('education:school:add')";
    private static final String SCHOOL_EDIT = "@ss.hasAuthority('education:school:edit')";
    private static final String SCHOOL_REMOVE = "@ss.hasAuthority('education:school:remove')";

    private final EducationOrganizationTreeService service;
    private final EducationMasterDataService masterDataService;

    @GetMapping("/tree")
    @PreAuthorize(SCHOOL_LIST)
    public R<List<EducationOrganizationNode>> tree(@RequestParam(required = false) Integer status) {
        return R.ok(service.tree(status));
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize(SCHOOL_ADD)
    @OperLog(module = "教育组织管理", type = OperLog.OperType.INSERT)
    public R<Long> add(@Valid @RequestBody EducationOrganizationForms.Organization form) {
        requireCreate(form.id());
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize(SCHOOL_EDIT)
    @OperLog(module = "教育组织管理", type = OperLog.OperType.UPDATE)
    public R<Long> edit(@Valid @RequestBody EducationOrganizationForms.Organization form) {
        requireEdit(form.id());
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize(SCHOOL_REMOVE)
    @OperLog(module = "教育组织管理", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(masterDataService.deleteSchools(request.ids()));
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
