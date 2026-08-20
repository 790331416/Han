package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduGradePromotionBatchPo;
import com.han.system.sdfz.education.domain.EducationPromotionForms;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 学年升级必须先创建预览批次，再由具备确认权限的人员二次确认。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/promotions")
@RequiredArgsConstructor
public class EducationPromotionController {
    private final EducationPromotionService service;

    @RepeatSubmit
    @PostMapping("/preview")
    @PreAuthorize(EducationPermissions.HAS_PROMOTION_PREVIEW)
    @OperLog(module = "学年升级", type = OperLog.OperType.INSERT)
    public R<EduGradePromotionBatchPo> preview(@Valid @RequestBody EducationPromotionForms.Preview form) {
        return R.ok(service.preview(form));
    }

    @RepeatSubmit
    @PostMapping("/confirm")
    @PreAuthorize(EducationPermissions.HAS_PROMOTION_CONFIRM)
    @OperLog(module = "学年升级", type = OperLog.OperType.UPDATE)
    public R<EduGradePromotionBatchPo> confirm(@Valid @RequestBody EducationPromotionForms.Confirm form) {
        return R.ok(service.confirm(form.batchId()));
    }

    @GetMapping("/detail")
    @PreAuthorize(EducationPermissions.HAS_PROMOTION_LIST)
    public R<EduGradePromotionBatchPo> detail(@RequestParam Long batchId) {
        return R.ok(service.detail(batchId));
    }

    @GetMapping("/list")
    @PreAuthorize(EducationPermissions.HAS_PROMOTION_LIST)
    public R<java.util.List<EduGradePromotionBatchPo>> list(@RequestParam Long schoolId) {
        return R.ok(service.list(schoolId));
    }
}
