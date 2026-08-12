package com.han.file.controller.admin;

import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.annotation.AdminAuth;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 文件管理接口（E-filemanage：管理端文件列表/删除，显式权限控制）。
 *
 * <p>路由 {@code /file/list}、{@code /file/remove/{ids}} 属于已发布契约，保持不变。
 */
@AdminAuth
@RestController("adminFileManageController")
@RequestMapping("/file")
@RequiredArgsConstructor
public class AFileManageController {

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
        // 回传服务端夹紧后的页码/页长，否则前端分页器按请求值算出的总页数是错的
        return R.ok(PageResult.of(result.rows(), result.total(), result.pageNum(), result.pageSize()));
    }

    /**
     * 批量删除（软删记录 + 尽力物理删除对象）。
     */
    @RepeatSubmit
    @PostMapping("/remove/{ids}")
    @PreAuthorize("@ss.hasAuthority('file:remove')")
    public R<Integer> remove(@PathVariable String ids) {
        return R.ok(fileStorageAccessService.removeByIds(parseIds(ids),
                SecurityContextHolder.getTenantId(), SecurityContextHolder.isAdmin()));
    }

    private List<Long> parseIds(String ids) {
        List<Long> idList = new ArrayList<>();
        if (ids == null || ids.isBlank()) {
            return idList;
        }
        for (String item : ids.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                idList.add(Long.valueOf(trimmed));
            } catch (NumberFormatException ex) {
                throw new BusinessException("文件ID格式错误: " + trimmed);
            }
        }
        return idList;
    }
}
