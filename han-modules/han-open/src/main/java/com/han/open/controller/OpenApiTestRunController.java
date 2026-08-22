package com.han.open.controller;

import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.domain.dto.OpenApiTestRunDTO;
import com.han.open.domain.vo.OpenApiTestRunVO;
import com.han.open.service.OpenApiTestRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 厂商门户在线调测审计接口；服务端不做代理，不接收任意 URL。 */
@AdminAuth
@Validated
@RestController
@RequestMapping("/open/debug/run")
@RequiredArgsConstructor
@Tag(name = "开放平台在线调测")
public class OpenApiTestRunController {

    private final OpenApiTestRunService testRunService;

    @PostMapping("/add")
    @RepeatSubmit
    @PreAuthorize("@ss.hasAuthority('open:grant:apply')")
    @Operation(summary = "提交在线调测审计记录")
    public R<OpenApiTestRunVO> add(@Valid @RequestBody OpenApiTestRunDTO request) {
        return R.ok(testRunService.add(request));
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('open:grant:query')")
    @Operation(summary = "查询在线调测审计记录")
    public R<List<OpenApiTestRunVO>> list(@RequestParam(required = false) Long appId) {
        return R.ok(testRunService.list(appId));
    }
}
