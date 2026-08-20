package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EducationPlaceTreeForms;
import com.han.system.sdfz.education.domain.EducationPlaceTreeNode;
import com.han.system.sdfz.education.domain.EducationForms;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@AdminAuth @RestController @RequestMapping("/system/education/place-tree") @RequiredArgsConstructor
public class EducationPlaceTreeController {
    private final EducationPlaceTreeService service;
    private final EducationCalendarService calendarService;
    @GetMapping @PreAuthorize("@ss.hasAuthority('education:room:list')")
    public R<List<EducationPlaceTreeNode>> tree(@RequestParam Long schoolId, @RequestParam(required = false) Integer status) { return R.ok(service.tree(schoolId, status)); }
    @RepeatSubmit @PostMapping @PreAuthorize("@ss.hasAuthority('education:room:add')") @OperLog(module = "场所管理", type = OperLog.OperType.INSERT)
    public R<Long> add(@Valid @RequestBody EducationPlaceTreeForms.Node form) { if (form.id() != null) throw new BusinessException("新增请求不能携带 ID"); return R.ok(service.save(form)); }
    @RepeatSubmit @PostMapping("/batch-floors") @PreAuthorize("@ss.hasAuthority('education:room:add')") @OperLog(module = "场所管理", type = OperLog.OperType.INSERT)
    public R<Integer> batchFloors(@Valid @RequestBody EducationPlaceTreeForms.FloorRange form) { return R.ok(service.createFloors(form)); }
    @RepeatSubmit @PostMapping("/edit") @PreAuthorize("@ss.hasAuthority('education:room:edit')") @OperLog(module = "场所管理", type = OperLog.OperType.UPDATE)
    public R<Long> edit(@Valid @RequestBody EducationPlaceTreeForms.Node form) { if (form.id() == null) throw new BusinessException("修改请求必须携带 ID"); return R.ok(service.save(form)); }
    @RepeatSubmit @PostMapping("/remove") @PreAuthorize("@ss.hasAuthority('education:room:remove')") @OperLog(module = "场所管理", type = OperLog.OperType.DELETE)
    public R<Integer> remove(@Valid @RequestBody EducationForms.DeleteRequest request) { return R.ok(calendarService.deleteRooms(request.ids())); }
}
