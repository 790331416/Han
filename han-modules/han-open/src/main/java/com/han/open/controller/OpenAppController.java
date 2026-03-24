package com.han.open.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.AdminAuth;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.dto.OpenAppStatusUpdateRequest;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 开放平台应用管理控制器。
 */
@AdminAuth
@RestController
@RequestMapping("/open/app")
@RequiredArgsConstructor
public class OpenAppController {

    private final IOpenAppService openAppService;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询应用列表。
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('open:app:list')")
    public R<PageResult<OpenAppVO>> list(@Validated OpenAppQuery query) {
        return R.ok(openAppService.selectPage(query));
    }

    /**
     * 查询应用详情。
     */
    @GetMapping("/{appId}")
    @PreAuthorize("@ss.hasAuthority('open:app:query')")
    public R<OpenAppVO> getInfo(@PathVariable Long appId) {
        return R.ok(openAppService.selectVoById(appId));
    }

    /**
     * 新增应用。
     */
    @RepeatSubmit
    @PostMapping
    @PreAuthorize("@ss.hasAuthority('open:app:add')")
    public R<Void> add(@Validated @RequestBody OpenAppDTO dto) {
        openAppService.insert(dto);
        return R.ok();
    }

    /**
     * 修改应用。
     */
    @RepeatSubmit
    @PostMapping("/edit")
    @PreAuthorize("@ss.hasAuthority('open:app:edit')")
    public R<Void> edit(@RequestBody Map<String, Object> requestBody) {
        OpenAppDTO dto = objectMapper.convertValue(requestBody, OpenAppDTO.class);
        bindCompatibleAppId(dto, requestBody);
        openAppService.update(dto);
        return R.ok();
    }

    /**
     * 删除应用。
     */
    @RepeatSubmit
    @PostMapping("/remove/{appId}")
    @PreAuthorize("@ss.hasAuthority('open:app:remove')")
    public R<Void> remove(@PathVariable Long appId) {
        openAppService.deleteById(appId);
        return R.ok();
    }

    /**
     * 重置应用密钥。
     */
    @RepeatSubmit
    @PostMapping("/resetSecret/{appId}")
    @PreAuthorize("@ss.hasAuthority('open:app:resetSecret')")
    public R<String> resetSecret(@PathVariable Long appId) {
        return R.ok(openAppService.resetAppSecret(appId));
    }

    /**
     * 变更应用状态。
     */
    @RepeatSubmit
    @PostMapping("/changeStatus")
    @PreAuthorize("@ss.hasAuthority('open:app:edit')")
    public R<Void> changeStatus(@RequestBody OpenAppStatusUpdateRequest request) {
        openAppService.updateStatus(request.getAppId(), request.resolveStatus());
        return R.ok();
    }

    private void bindCompatibleAppId(OpenAppDTO dto, Map<String, Object> requestBody) {
        if (dto == null || dto.getAppId() != null || requestBody == null || requestBody.isEmpty()) {
            return;
        }
        Long appId = toLong(requestBody.get("appId"));
        if (appId == null) {
            appId = toLong(requestBody.get("id"));
        }
        if (appId != null) {
            dto.setAppId(appId);
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
