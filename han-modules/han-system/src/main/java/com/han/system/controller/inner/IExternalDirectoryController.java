package com.han.system.controller.inner;

import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.api.system.domain.EducationPersonDirectoryVO;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.sdfz.education.EducationOpenDirectoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 供 han-open 调用的受限教育目录内部接口，浏览器和第三方应用不得直接访问。 */
@InnerAuth
@RestController("innerExternalDirectoryController")
@RequestMapping("/inner/system/external/directory")
@RequiredArgsConstructor
public class IExternalDirectoryController {

    private final EducationOpenDirectoryService directoryService;

    @GetMapping("/people")
    public R<PageResult<EducationPersonDirectoryVO>> people(
            @RequestParam Long tenantId,
            @RequestParam List<Long> schoolIds,
            @RequestParam(required = false) String personType,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return R.ok(directoryService.people(tenantId, schoolIds, personType, status, updatedAfter, pageNum, pageSize));
    }

    @GetMapping("/devices")
    public R<PageResult<EducationDeviceDirectoryVO>> devices(
            @RequestParam Long tenantId,
            @RequestParam List<Long> schoolIds,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime updatedAfter,
            @RequestParam(required = false) Integer pageNum,
            @RequestParam(required = false) Integer pageSize) {
        return R.ok(directoryService.devices(tenantId, schoolIds, status, updatedAfter, pageNum, pageSize));
    }

    @GetMapping("/schools/names")
    public R<Map<Long, String>> schoolNames(@RequestParam Long tenantId,
                                            @RequestParam List<Long> schoolIds) {
        return R.ok(directoryService.schoolNames(tenantId, schoolIds));
    }
}
