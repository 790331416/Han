package com.han.workflow.service.impl;

import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ForbiddenException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.workflow.domain.dto.ProcessStartDTO;
import com.han.workflow.domain.dto.TaskCompleteDTO;
import com.han.workflow.domain.dto.TaskQueryDTO;
import com.han.workflow.domain.vo.ProcessInstanceVO;
import com.han.workflow.domain.vo.TaskVO;
import com.han.workflow.security.WorkflowAccessChecker;
import com.han.workflow.service.IProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ProcessInstanceServiceImpl implements IProcessInstanceService {

    private final RuntimeService runtimeService;
    private final TaskService taskService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final WorkflowAccessChecker accessChecker;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProcessInstanceVO startProcess(ProcessStartDTO dto) {
        if (dto == null) {
            throw new BusinessException("流程启动参数不能为空");
        }
        String processKey = trimToNull(dto.resolveProcessKey());
        if (!StringUtils.hasText(processKey)) {
            throw new BusinessException("流程Key不能为空");
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        if (dto.getVariables() != null) {
            variables.putAll(dto.getVariables());
        }
        if (StringUtils.hasText(dto.getTitle())) {
            variables.putIfAbsent("title", dto.getTitle().trim());
        }
        if (StringUtils.hasText(dto.getNextAssignee())) {
            variables.putIfAbsent("nextAssignee", dto.getNextAssignee().trim());
        }

        String currentUserId = requiredCurrentUserId();
        String tenantId = accessChecker.currentTenantId();
        String previousAuthenticatedUserId = Authentication.getAuthenticatedUserId();
        Authentication.setAuthenticatedUserId(currentUserId);
        try {
            ProcessInstance processInstance = hasTenantScopedDefinition(processKey, tenantId)
                    ? runtimeService.startProcessInstanceByKeyAndTenantId(
                            processKey, trimToNull(dto.getBusinessKey()), variables, tenantId)
                    : runtimeService.startProcessInstanceByKey(
                            processKey, trimToNull(dto.getBusinessKey()), variables);
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstance.getProcessInstanceId())
                    .singleResult();
            return historic != null ? toProcessVO(historic) : toProcessVO(processInstance);
        } finally {
            Authentication.setAuthenticatedUserId(previousAuthenticatedUserId);
        }
    }

    @Override
    public PageResult<TaskVO> listMyTodoTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = safeQuery(dto);
        TaskQuery taskQuery = taskService.createTaskQuery().active();
        applyTenant(taskQuery);
        List<Task> tasks = taskQuery
                .taskAssignee(requiredCurrentUserId())
                .orderByTaskCreateTime()
                .desc()
                .list();
        return paginate(filterTasks(mapTodoTasks(tasks), query), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<TaskVO> listMyDoneTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = safeQuery(dto);
        HistoricTaskInstanceQuery taskQuery = historyService.createHistoricTaskInstanceQuery();
        applyTenant(taskQuery);
        List<HistoricTaskInstance> tasks = taskQuery
                .taskAssignee(requiredCurrentUserId())
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc()
                .list();
        return paginate(filterTasks(mapDoneTasks(tasks), query), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<ProcessInstanceVO> listMyStartedProcess(TaskQueryDTO dto) {
        TaskQueryDTO query = safeQuery(dto);
        HistoricProcessInstanceQuery processQuery = historyService.createHistoricProcessInstanceQuery();
        applyTenant(processQuery);
        List<HistoricProcessInstance> processes = processQuery
                .startedBy(requiredCurrentUserId())
                .orderByProcessInstanceStartTime()
                .desc()
                .list();
        List<ProcessInstanceVO> rows = processes.stream()
                .map(this::toProcessVO)
                .filter(item -> matchesProcess(item, query))
                .toList();
        return paginate(rows, query.getPageNum(), query.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(TaskCompleteDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getTaskId())) {
            throw new BusinessException("任务ID不能为空");
        }

        Task task = requireTask(dto.getTaskId());
        accessChecker.checkCanComplete(task);
        if (!StringUtils.hasText(task.getAssignee())) {
            // 候选人办理前先签收，保证 ACT_HI_TASKINST 上留下真实办理人
            taskService.claim(task.getId(), requiredCurrentUserId());
        }
        if (StringUtils.hasText(dto.getComment())) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), dto.getComment().trim());
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        if (dto.getVariables() != null) {
            variables.putAll(dto.getVariables());
        }
        if (StringUtils.hasText(dto.getResult())) {
            variables.putIfAbsent("result", dto.getResult().trim());
        }

        if (variables.isEmpty()) {
            taskService.complete(task.getId());
            return;
        }
        taskService.complete(task.getId(), variables);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(String taskId, String targetUserId, String reason) {
        Task task = requireTask(taskId);
        accessChecker.checkCanTransfer(task, "转办");
        String assignee = trimToNull(targetUserId);
        if (!StringUtils.hasText(assignee)) {
            throw new BusinessException("目标办理人不能为空");
        }
        if (StringUtils.hasText(reason)) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), reason.trim());
        }
        taskService.setAssignee(task.getId(), assignee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delegateTask(String taskId, String targetUserId, String reason) {
        Task task = requireTask(taskId);
        accessChecker.checkCanTransfer(task, "委派");
        String assignee = trimToNull(targetUserId);
        if (!StringUtils.hasText(assignee)) {
            throw new BusinessException("委派人不能为空");
        }
        if (StringUtils.hasText(reason)) {
            taskService.addComment(task.getId(), task.getProcessInstanceId(), reason.trim());
        }
        taskService.delegateTask(task.getId(), assignee);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void claimTask(String taskId) {
        Task task = requireTask(taskId);
        accessChecker.checkCanClaim(task);
        taskService.claim(task.getId(), requiredCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unclaimTask(String taskId) {
        Task task = requireTask(taskId);
        accessChecker.checkCanUnclaim(task);
        taskService.unclaim(task.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcess(String processInstanceId, String reason) {
        ProcessInstance process = ensureActiveProcess(processInstanceId);
        accessChecker.checkInstanceOwner(process.getTenantId(), process.getStartUserId(), "终止");
        runtimeService.deleteProcessInstance(processInstanceId, trimToNull(reason));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspendProcess(String processInstanceId) {
        ProcessInstance process = ensureActiveProcess(processInstanceId);
        accessChecker.checkInstanceOwner(process.getTenantId(), process.getStartUserId(), "挂起");
        runtimeService.suspendProcessInstanceById(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void activateProcess(String processInstanceId) {
        HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (process == null) {
            throw new BusinessException("流程实例不存在");
        }
        accessChecker.checkInstanceOwner(process.getTenantId(), process.getStartUserId(), "激活");
        runtimeService.activateProcessInstanceById(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcess(String processInstanceId, String reason) {
        ProcessInstance runtimeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeProcess != null) {
            accessChecker.checkInstanceOwner(runtimeProcess.getTenantId(), runtimeProcess.getStartUserId(), "删除");
            runtimeService.deleteProcessInstance(processInstanceId, trimToNull(reason));
            return;
        }
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicProcess == null) {
            throw new BusinessException("流程实例不存在");
        }
        accessChecker.checkInstanceOwner(historicProcess.getTenantId(), historicProcess.getStartUserId(), "删除");
        // 已结束实例的历史是审计资产，删除即不可恢复，仅允许平台管理员执行
        if (!accessChecker.isAdmin()) {
            throw new BusinessException("流程已结束，历史审计记录仅允许平台管理员删除");
        }
        historyService.deleteHistoricProcessInstance(processInstanceId);
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        ProcessInstance runtimeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeProcess == null) {
            return Map.of();
        }
        accessChecker.checkInstanceOwner(runtimeProcess.getTenantId(), runtimeProcess.getStartUserId(), "查看");
        return runtimeService.getVariables(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        ProcessInstance process = ensureActiveProcess(processInstanceId);
        accessChecker.checkInstanceOwner(process.getTenantId(), process.getStartUserId(), "修改");
        runtimeService.setVariables(processInstanceId, variables == null ? Map.of() : variables);
    }

    @Override
    public InputStream getProcessDiagram(String processInstanceId) {
        requireReadableProcess(processInstanceId);
        ProcessDefinition definition = resolveProcessDefinition(resolveProcessDefinitionId(processInstanceId));
        if (definition == null || !StringUtils.hasText(definition.getDiagramResourceName())) {
            return InputStream.nullInputStream();
        }
        InputStream stream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getDiagramResourceName());
        return stream != null ? stream : InputStream.nullInputStream();
    }

    @Override
    public List<TaskVO> getProcessHistory(String processInstanceId) {
        requireReadableProcess(processInstanceId);
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime()
                .asc()
                .list();
        List<TaskVO> rows = mapDoneTasks(tasks);
        fillTaskComments(processInstanceId, rows);
        return rows;
    }

    /**
     * 回填审批意见：{@code completeTask} 写进 ACT_HI_COMMENT 的意见此前没有任何读取入口
     */
    private void fillTaskComments(String processInstanceId, List<TaskVO> rows) {
        List<Comment> comments = taskService.getProcessInstanceComments(processInstanceId);
        if (comments == null || comments.isEmpty()) {
            return;
        }
        Map<String, String> commentByTask = new LinkedHashMap<>();
        for (Comment comment : comments) {
            if (!StringUtils.hasText(comment.getTaskId()) || !StringUtils.hasText(comment.getFullMessage())) {
                continue;
            }
            commentByTask.merge(comment.getTaskId(), comment.getFullMessage(), (left, right) -> left + "\n" + right);
        }
        for (TaskVO row : rows) {
            row.setComment(commentByTask.get(row.getTaskId()));
        }
    }

    private List<TaskVO> mapTodoTasks(List<Task> tasks) {
        Map<String, ProcessDefinitionMeta> definitionCache = new HashMap<>();
        Map<String, HistoricProcessInstance> processCache = new HashMap<>();
        List<TaskVO> rows = new ArrayList<>(tasks.size());
        for (Task task : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setProcessDefinitionId(task.getProcessDefinitionId());
            vo.setAssignee(task.getAssignee());
            vo.setAssigneeName(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
            vo.setDueDate(toLocalDateTime(task.getDueDate()));
            fillTaskProcessInfo(vo, definitionCache, processCache);
            rows.add(vo);
        }
        return rows;
    }

    private List<TaskVO> mapDoneTasks(List<HistoricTaskInstance> tasks) {
        Map<String, ProcessDefinitionMeta> definitionCache = new HashMap<>();
        Map<String, HistoricProcessInstance> processCache = new HashMap<>();
        List<TaskVO> rows = new ArrayList<>(tasks.size());
        for (HistoricTaskInstance task : tasks) {
            TaskVO vo = new TaskVO();
            vo.setTaskId(task.getId());
            vo.setTaskName(task.getName());
            vo.setTaskDefinitionKey(task.getTaskDefinitionKey());
            vo.setProcessInstanceId(task.getProcessInstanceId());
            vo.setProcessDefinitionId(task.getProcessDefinitionId());
            vo.setAssignee(task.getAssignee());
            vo.setAssigneeName(task.getAssignee());
            vo.setCreateTime(toLocalDateTime(task.getCreateTime()));
            vo.setClaimTime(toLocalDateTime(task.getClaimTime()));
            vo.setDueDate(toLocalDateTime(task.getDueDate()));
            vo.setEndTime(toLocalDateTime(task.getEndTime()));
            vo.setDuration(task.getDurationInMillis());
            fillTaskProcessInfo(vo, definitionCache, processCache);
            rows.add(vo);
        }
        return rows;
    }

    private void fillTaskProcessInfo(TaskVO vo,
                                     Map<String, ProcessDefinitionMeta> definitionCache,
                                     Map<String, HistoricProcessInstance> processCache) {
        ProcessDefinitionMeta definitionMeta = resolveProcessDefinitionMeta(vo.getProcessDefinitionId(), definitionCache);
        vo.setProcessDefinitionKey(definitionMeta.key());
        vo.setProcessDefinitionName(definitionMeta.name());

        HistoricProcessInstance process = resolveHistoricProcess(vo.getProcessInstanceId(), processCache);
        if (process == null) {
            return;
        }
        vo.setBusinessKey(process.getBusinessKey());
        vo.setStartUserId(process.getStartUserId());
        vo.setStartUserName(process.getStartUserId());
    }

    private ProcessInstanceVO toProcessVO(ProcessInstance process) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setProcessInstanceId(process.getProcessInstanceId());
        vo.setInstanceId(process.getProcessInstanceId());
        vo.setProcessDefinitionId(process.getProcessDefinitionId());
        ProcessDefinition definition = resolveProcessDefinition(process.getProcessDefinitionId());
        if (definition != null) {
            vo.setProcessDefinitionKey(definition.getKey());
            vo.setProcessDefinitionName(definition.getName());
            vo.setProcessDefinitionVersion(definition.getVersion());
        }
        vo.setBusinessKey(process.getBusinessKey());
        vo.setStartUserId(requiredCurrentUserId());
        vo.setStartUserName(SecurityContextHolder.getUsername());
        vo.setStartTime(LocalDateTime.now());
        vo.setStatus(resolveRuntimeStatus(process.getProcessInstanceId()));
        fillCurrentTaskSummary(vo, process.getProcessInstanceId());
        return vo;
    }

    private ProcessInstanceVO toProcessVO(HistoricProcessInstance process) {
        ProcessInstanceVO vo = new ProcessInstanceVO();
        vo.setProcessInstanceId(process.getId());
        vo.setInstanceId(process.getId());
        vo.setProcessDefinitionId(process.getProcessDefinitionId());
        vo.setProcessDefinitionKey(process.getProcessDefinitionKey());
        vo.setProcessDefinitionName(process.getProcessDefinitionName());
        vo.setProcessDefinitionVersion(process.getProcessDefinitionVersion());
        vo.setBusinessKey(process.getBusinessKey());
        vo.setStartUserId(process.getStartUserId());
        vo.setStartUserName(process.getStartUserId());
        vo.setStartTime(toLocalDateTime(process.getStartTime()));
        vo.setEndTime(toLocalDateTime(process.getEndTime()));
        vo.setDuration(process.getDurationInMillis());
        vo.setStatus(resolveProcessStatus(process));
        vo.setSuspended("suspended".equals(vo.getStatus()));
        vo.setEnded(process.getEndTime() != null);
        fillCurrentTaskSummary(vo, process.getId());
        return vo;
    }

    private void fillCurrentTaskSummary(ProcessInstanceVO vo, String processInstanceId) {
        List<Task> tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();
        if (tasks.isEmpty()) {
            return;
        }
        vo.setCurrentActivityName(joinDistinct(tasks.stream().map(Task::getName).toList()));
        vo.setCurrentAssignee(joinDistinct(tasks.stream().map(Task::getAssignee).toList()));
    }

    private List<TaskVO> filterTasks(List<TaskVO> rows, TaskQueryDTO query) {
        return rows.stream()
                .filter(item -> containsIgnoreCase(item.getTaskName(), trimToNull(query.getTaskName())))
                .filter(item -> containsIgnoreCase(item.getProcessDefinitionName(), trimToNull(query.resolveProcessName())))
                .filter(item -> containsIgnoreCase(item.getBusinessKey(), trimToNull(query.getBusinessKey())))
                .toList();
    }

    private boolean matchesProcess(ProcessInstanceVO item, TaskQueryDTO query) {
        return containsIgnoreCase(item.getProcessDefinitionName(), trimToNull(query.resolveProcessName()))
                && containsIgnoreCase(item.getBusinessKey(), trimToNull(query.getBusinessKey()))
                && (!StringUtils.hasText(query.getStatus()) || query.getStatus().equals(item.getStatus()));
    }

    private <T> PageResult<T> paginate(List<T> rows, Integer rawPageNum, Integer rawPageSize) {
        int pageNum = normalizePageNum(rawPageNum);
        int pageSize = normalizePageSize(rawPageSize);
        int fromIndex = Math.min((pageNum - 1) * pageSize, rows.size());
        int toIndex = Math.min(fromIndex + pageSize, rows.size());
        return PageResult.of(rows.subList(fromIndex, toIndex), rows.size(), pageNum, pageSize);
    }

    private Task requireTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new BusinessException("任务ID不能为空");
        }
        Task task = taskService.createTaskQuery().taskId(taskId.trim()).singleResult();
        if (task == null) {
            throw new BusinessException("任务不存在");
        }
        return task;
    }

    private ProcessInstance ensureActiveProcess(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            throw new BusinessException("流程实例不存在或已结束");
        }
        return processInstance;
    }

    /**
     * 读取一个流程实例前的可见性校验：同租户，且为发起人 / 流程参与人 / 平台管理员
     */
    private HistoricProcessInstance requireReadableProcess(String processInstanceId) {
        if (!StringUtils.hasText(processInstanceId)) {
            throw new BusinessException("流程实例ID不能为空");
        }
        HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId.trim())
                .singleResult();
        if (process == null) {
            throw new BusinessException("流程实例不存在");
        }
        accessChecker.checkTenant(process.getTenantId());
        if (accessChecker.isAdmin()) {
            return process;
        }
        String currentUserId = requiredCurrentUserId();
        if (currentUserId.equals(trimToNull(process.getStartUserId()))) {
            return process;
        }
        boolean involved = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId.trim())
                .involvedUser(currentUserId)
                .count() > 0;
        if (!involved) {
            throw new ForbiddenException("无权查看他人的流程");
        }
        return process;
    }

    /**
     * 当前租户下是否存在该 key 的流程定义。
     *
     * <p>启用租户隔离之前部署的流程定义在 Flowable 里没有租户标记，按租户启动会直接报
     * 「no processes deployed with key」。这里先探测，探测不到就回退到不带租户的启动，
     * 保证存量环境的流程还能发起。
     */
    private boolean hasTenantScopedDefinition(String processKey, String tenantId) {
        if (tenantId == null) {
            return false;
        }
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .processDefinitionTenantId(tenantId)
                .count() > 0;
    }

    private void applyTenant(TaskQuery query) {
        String tenantId = accessChecker.currentTenantId();
        if (tenantId == null) {
            return;
        }
        query.or().taskTenantId(tenantId).taskWithoutTenantId().endOr();
    }

    private void applyTenant(HistoricTaskInstanceQuery query) {
        String tenantId = accessChecker.currentTenantId();
        if (tenantId == null) {
            return;
        }
        query.or().taskTenantId(tenantId).taskWithoutTenantId().endOr();
    }

    private void applyTenant(HistoricProcessInstanceQuery query) {
        String tenantId = accessChecker.currentTenantId();
        if (tenantId == null) {
            return;
        }
        query.or().processInstanceTenantId(tenantId).processInstanceWithoutTenantId().endOr();
    }

    private String resolveProcessDefinitionId(String processInstanceId) {
        ProcessInstance runtimeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeProcess != null) {
            return runtimeProcess.getProcessDefinitionId();
        }
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return historicProcess != null ? historicProcess.getProcessDefinitionId() : null;
    }

    private ProcessDefinition resolveProcessDefinition(String processDefinitionId) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        return repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(processDefinitionId)
                .singleResult();
    }

    private ProcessDefinitionMeta resolveProcessDefinitionMeta(String processDefinitionId,
                                                              Map<String, ProcessDefinitionMeta> cache) {
        if (!StringUtils.hasText(processDefinitionId)) {
            return ProcessDefinitionMeta.EMPTY;
        }
        return cache.computeIfAbsent(processDefinitionId, key -> {
            ProcessDefinition definition = resolveProcessDefinition(key);
            if (definition == null) {
                return ProcessDefinitionMeta.EMPTY;
            }
            return new ProcessDefinitionMeta(definition.getKey(), definition.getName());
        });
    }

    private HistoricProcessInstance resolveHistoricProcess(String processInstanceId,
                                                           Map<String, HistoricProcessInstance> cache) {
        if (!StringUtils.hasText(processInstanceId)) {
            return null;
        }
        if (cache.containsKey(processInstanceId)) {
            return cache.get(processInstanceId);
        }
        HistoricProcessInstance process = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        cache.put(processInstanceId, process);
        return process;
    }

    private String resolveProcessStatus(HistoricProcessInstance process) {
        if (process.getEndTime() == null) {
            return resolveRuntimeStatus(process.getId());
        }
        if (StringUtils.hasText(process.getDeleteReason()) && !"completed".equalsIgnoreCase(process.getDeleteReason())) {
            return "terminated";
        }
        return "completed";
    }

    private String resolveRuntimeStatus(String processInstanceId) {
        boolean suspended = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .suspended()
                .count() > 0;
        return suspended ? "suspended" : "running";
    }

    private String requiredCurrentUserId() {
        return accessChecker.requiredCurrentUserId();
    }

    private TaskQueryDTO safeQuery(TaskQueryDTO query) {
        return query != null ? query : new TaskQueryDTO();
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 10;
        }
        return Math.min(pageSize, 100);
    }

    private LocalDateTime toLocalDateTime(Date value) {
        if (value == null) {
            return null;
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private boolean containsIgnoreCase(String source, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return StringUtils.hasText(source) && source.toLowerCase().contains(keyword.toLowerCase());
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String joinDistinct(List<String> values) {
        return values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private record ProcessDefinitionMeta(String key, String name) {
        private static final ProcessDefinitionMeta EMPTY = new ProcessDefinitionMeta(null, null);
    }
}
