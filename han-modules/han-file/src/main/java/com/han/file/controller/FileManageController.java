package com.han.file.controller;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.RepeatSubmit;
import com.han.common.security.context.SecurityContextHolder;
import com.han.file.service.FileStorageAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 文件管理接口（E-filemanage：管理端文件列表/删除，显式权限控制）。
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileManageController {

    private final FileStorageAccessService fileStorageAccessService;

    /**
     * 文件分页列表。
     */
    @GetMapping("/list")
    @PreAuthorize("@ss.hasAuthority('file:query')")
    public R<PageResult<Map<String, Object>>> list(@RequestParam(required = false) String fileName,
                                                   @RequestParam(required = false) String fileType,
                                                   @RequestParam(required = false) String beginTime,
                                                   @RequestParam(required = false) String endTime,
                                                   @RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize) {
        FileStorageAccessService.PageQueryResult result = fileStorageAccessService.page(
                fileName, fileType, beginTime, endTime, pageNum, pageSize,
                SecurityContextHolder.getTenantId(), SecurityContextHolder.isAdmin());
        return R.ok(PageResult.of(result.rows(), result.total(), pageNum, pageSize));
    }

    /**
     * 批量删除（软删记录 + 尽力物理删除对象）。
     */
    @RepeatSubmit
    @PostMapping("/remove/{ids}")
    @PreAuthorize("@ss.hasAuthority('file:remove')")
    public R<Integer> remove(@PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .map(Long::valueOf)
                .toList();
        return R.ok(fileStorageAccessService.removeByIds(idList,
                SecurityContextHolder.getTenantId(), SecurityContextHolder.isAdmin()));
    }
}
