package com.han.system.sdfz.education;

import com.han.common.core.domain.R;
import com.han.common.log.annotation.OperLog;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.system.sdfz.education.domain.EduUserScopePo;
import com.han.system.sdfz.education.domain.EducationScopeForms;
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

/** 教育数据范围由菜单权限控制，并始终限制在当前登录租户内。 */
@AdminAuth
@RestController
@RequestMapping("/system/education/scopes")
@RequiredArgsConstructor
public class EducationDataScopeController {
    private final EducationDataScopeService service;

    @GetMapping("/list")
    @PreAuthorize(EducationPermissions.HAS_SCOPE_LIST)
    public R<List<EduUserScopePo>> list(@RequestParam Long userId) {
        return R.ok(service.listForUser(userId));
    }

    @PostMapping("/replace")
    @RepeatSubmit
    @PreAuthorize(EducationPermissions.HAS_SCOPE_EDIT)
    @OperLog(module = "教育数据范围", type = OperLog.OperType.UPDATE)
    public R<Integer> replace(@Valid @RequestBody EducationScopeForms.Replace form) {
        return R.ok(service.replaceForUser(form));
    }
}
