package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EducationRegionForms;
import com.han.system.sdfz.education.domain.EducationRegionNode;
import com.han.system.sdfz.education.domain.EducationRegionSearchOption;
import com.han.system.sdfz.education.domain.EduRegionPo;
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

/** 区域树按列表、新增、修改、删除权限分别控制。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/regions")
@RequiredArgsConstructor
public class EducationRegionTreeController {
    private final EducationRegionTreeService service;

    @GetMapping("/tree")
    @PreAuthorize(EducationPermissions.HAS_REGION_LIST + " or " + EducationPermissions.HAS_SCOPE_LIST)
    public R<List<EducationRegionNode>> tree(@RequestParam(required = false) Integer status) {
        return R.ok(service.tree(status));
    }

    @GetMapping("/options")
    @PreAuthorize(EducationPermissions.HAS_REGION_LIST + " or " + EducationPermissions.HAS_SCOPE_LIST
            + " or @ss.hasAuthority('education:school:add') or @ss.hasAuthority('education:school:edit')")
    public R<List<EduRegionPo>> options(@RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) Long parentId) {
        return R.ok(service.options(keyword, parentId));
    }

    /** 按名称或编码搜索，并返回完整路径供通用区域选择器展示。 */
    @GetMapping("/options/search")
    @PreAuthorize(EducationPermissions.HAS_REGION_LIST + " or " + EducationPermissions.HAS_SCOPE_LIST
            + " or @ss.hasAuthority('education:school:add') or @ss.hasAuthority('education:school:edit')")
    public R<List<EducationRegionSearchOption>> searchOptions(@RequestParam String keyword) {
        return R.ok(service.searchOptions(keyword));
    }

    /**
     * 区域管理树按需展开，不能把全国四级区域一次性返回给浏览器。
     */
    @GetMapping("/children")
    @PreAuthorize(EducationPermissions.HAS_REGION_LIST + " or " + EducationPermissions.HAS_SCOPE_LIST)
    public R<List<EduRegionPo>> children(@RequestParam(required = false) Long parentId,
                                         @RequestParam(required = false) Integer status) {
        return R.ok(service.children(parentId, status));
    }

    /**
     * 回填已关联区域的省市区路径，供级联选择器编辑时定位。
     */
    @GetMapping("/path")
    @PreAuthorize(EducationPermissions.HAS_REGION_LIST + " or " + EducationPermissions.HAS_SCOPE_LIST
            + " or @ss.hasAuthority('education:school:add') or @ss.hasAuthority('education:school:edit')")
    public R<List<EduRegionPo>> path(@RequestParam Long regionId) {
        return R.ok(service.path(regionId));
    }

    @RepeatSubmit
    @PostMapping
    @PreAuthorize(EducationPermissions.HAS_REGION_ADD)
    @OperLog(module = "教育区域管理", type = OperLog.OperType.INSERT)
    public R<Long> add(@Valid @RequestBody EducationRegionForms.Region form) {
        if (form.id() != null) throw new BusinessException("新增请求不能携带 ID");
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize(EducationPermissions.HAS_REGION_EDIT)
    @OperLog(module = "教育区域管理", type = OperLog.OperType.UPDATE)
    public R<Long> edit(@Valid @RequestBody EducationRegionForms.Region form) {
        if (form.id() == null) throw new BusinessException("修改请求必须携带 ID");
        return R.ok(service.save(form));
    }

    @RepeatSubmit
    @PostMapping("/remove")
    @PreAuthorize(EducationPermissions.HAS_REGION_REMOVE)
    @OperLog(module = "教育区域管理", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationForms.DeleteRequest request) {
        return R.ok(service.delete(request.ids()));
    }
}
