package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EducationClassTreeForms;
import com.han.system.sdfz.education.domain.EducationClassTreeNode;
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

@AdminAuth
@RestController
@RequestMapping("/system/education/class-tree")
@RequiredArgsConstructor
public class EducationClassTreeController {
    private final EducationClassTreeService service;
    private final EducationMasterDataService masterDataService;
    @GetMapping
    @PreAuthorize("@ss.hasAuthority('education:class:list')")
    public R<List<EducationClassTreeNode>> tree(@RequestParam Long schoolId, @RequestParam(required = false) Long academicYearId, @RequestParam(required = false) Integer status) {
        return R.ok(service.tree(schoolId, academicYearId, status));
    }
    @RepeatSubmit @PostMapping @PreAuthorize("@ss.hasAuthority('education:class:add')")
    @OperLog(module = "教学组织管理", type = OperLog.OperType.INSERT)
    public R<Long> add(@Valid @RequestBody EducationClassTreeForms.Node form) {
        if (form.id() != null) throw new BusinessException("新增请求不能携带 ID"); return R.ok(service.save(form));
    }
    @RepeatSubmit @PostMapping("/batch") @PreAuthorize("@ss.hasAuthority('education:class:add')")
    @OperLog(module = "教学组织管理", type = OperLog.OperType.INSERT)
    public R<Integer> batch(@Valid @RequestBody EducationClassTreeForms.Range form) {
        return R.ok(service.createRange(form));
    }
    @RepeatSubmit @PostMapping("/edit") @PreAuthorize("@ss.hasAuthority('education:class:edit')")
    @OperLog(module = "教学组织管理", type = OperLog.OperType.UPDATE)
    public R<Long> edit(@Valid @RequestBody EducationClassTreeForms.Node form) {
        if (form.id() == null) throw new BusinessException("修改请求必须携带 ID"); return R.ok(service.save(form));
    }
    @RepeatSubmit @PostMapping("/remove") @PreAuthorize("@ss.hasAuthority('education:class:remove')")
    @OperLog(module = "教学组织管理", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationForms.DeleteRequest request) { return R.ok(masterDataService.deleteClasses(request.ids())); }
}
