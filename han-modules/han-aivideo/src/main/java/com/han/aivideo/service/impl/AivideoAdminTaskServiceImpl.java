package com.han.aivideo.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.aivideo.domain.po.AiVideoGenerationTaskPo;
import com.han.aivideo.domain.query.AivideoTaskQuery;
import com.han.aivideo.mapper.AiVideoGenerationTaskMapper;
import com.han.aivideo.service.IAivideoAdminTaskService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AivideoAdminTaskServiceImpl extends AivideoServiceSupport implements IAivideoAdminTaskService {

    private final AiVideoGenerationTaskMapper taskMapper;

    @Override
    public PageResult<AiVideoGenerationTaskPo> selectPage(AivideoTaskQuery query) {
        AivideoTaskQuery safeQuery = query != null ? query : new AivideoTaskQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiVideoGenerationTaskPo> page = taskMapper.selectPage(
                new Page<>(pageNum, pageSize),
                buildTaskWrapper(safeQuery)
        );
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiVideoGenerationTaskPo selectById(Long taskId) {
        if (taskId == null) {
            throw new BusinessException("任务ID不能为空");
        }
        AiVideoGenerationTaskPo task = taskMapper.selectById(taskId);
        if (task == null || !Integer.valueOf(DEL_FLAG_NORMAL).equals(task.getDelFlag())) {
            throw new BusinessException("任务不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(task.getTenantId())) {
            throw new BusinessException("无权访问该任务");
        }
        return task;
    }

    private LambdaQueryWrapper<AiVideoGenerationTaskPo> buildTaskWrapper(AivideoTaskQuery query) {
        LambdaQueryWrapper<AiVideoGenerationTaskPo> wrapper = new LambdaQueryWrapper<AiVideoGenerationTaskPo>()
                .eq(query.getProjectId() != null, AiVideoGenerationTaskPo::getProjectId, query.getProjectId())
                .eq(query.getTenantId() != null && currentUserIsAdmin(), AiVideoGenerationTaskPo::getTenantId, query.getTenantId())
                .eq(StringUtils.hasText(query.getTaskType()), AiVideoGenerationTaskPo::getTaskType, query.getTaskType())
                .eq(StringUtils.hasText(query.getTaskStatus()), AiVideoGenerationTaskPo::getTaskStatus, query.getTaskStatus())
                .eq(AiVideoGenerationTaskPo::getDelFlag, DEL_FLAG_NORMAL)
                .orderByDesc(AiVideoGenerationTaskPo::getUpdateTime)
                .orderByDesc(AiVideoGenerationTaskPo::getCreateTime);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiVideoGenerationTaskPo::getTenantId, tenantId);
        }
        return wrapper;
    }
}
