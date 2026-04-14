package com.han.workflow.service.impl;

import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.workflow.domain.dto.ProcessStartDTO;
import com.han.workflow.domain.dto.TaskCompleteDTO;
import com.han.workflow.domain.dto.TaskQueryDTO;
import com.han.workflow.domain.vo.ProcessInstanceVO;
import com.han.workflow.domain.vo.TaskVO;
import com.han.workflow.service.IProcessInstanceService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
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
        Authentication.setAuthenticatedUserId(currentUserId);
        try {
            ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
                    processKey,
                    trimToNull(dto.getBusinessKey()),
                    variables
            );
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstance.getProcessInstanceId())
                    .singleResult();
            return historic != null ? toProcessVO(historic) : toProcessVO(processInstance);
        } finally {
            Authentication.setAuthenticatedUserId(null);
        }
    }

    @Override
    public PageResult<TaskVO> listMyTodoTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = safeQuery(dto);
        List<Task> tasks = taskService.createTaskQuery()
                .active()
                .taskAssignee(requiredCurrentUserId())
                .orderByTaskCreateTime()
                .desc()
                .list();
        return paginate(filterTasks(mapTodoTasks(tasks), query), query.getPageNum(), query.getPageSize());
    }

    @Override
    public PageResult<TaskVO> listMyDoneTasks(TaskQueryDTO dto) {
        TaskQueryDTO query = safeQuery(dto);
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
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
        List<HistoricProcessInstance> processes = historyService.createHistoricProcessInstanceQuery()
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
        taskService.claim(requireTask(taskId).getId(), requiredCurrentUserId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unclaimTask(String taskId) {
        taskService.unclaim(requireTask(taskId).getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelProcess(String processInstanceId, String reason) {
        ensureActiveProcess(processInstanceId);
        runtimeService.deleteProcessInstance(processInstanceId, trimToNull(reason));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void suspendProcess(String processInstanceId) {
        ensureActiveProcess(processInstanceId);
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
        runtimeService.activateProcessInstanceById(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProcess(String processInstanceId, String reason) {
        ProcessInstance runtimeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (runtimeProcess != null) {
            runtimeService.deleteProcessInstance(processInstanceId, trimToNull(reason));
            return;
        }
        HistoricProcessInstance historicProcess = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicProcess == null) {
            throw new BusinessException("流程实例不存在");
        }
        historyService.deleteHistoricProcessInstance(processInstanceId);
    }

    @Override
    public Map<String, Object> getProcessVariables(String processInstanceId) {
        ProcessInstance runtimeProcess = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        return runtimeProcess == null ? Map.of() : runtimeService.getVariables(processInstanceId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setProcessVariables(String processInstanceId, Map<String, Object> variables) {
        ensureActiveProcess(processInstanceId);
        runtimeService.setVariables(processInstanceId, variables == null ? Map.of() : variables);
    }

    @Override
    public InputStream getProcessDiagram(String processInstanceId) {
        ProcessDefinition definition = resolveProcessDefinition(resolveProcessDefinitionId(processInstanceId));
        if (definition == null || !StringUtils.hasText(definition.getDiagramResourceName())) {
            return InputStream.nullInputStream();
        }
        InputStream stream = repositoryService.getResourceAsStream(definition.getDeploymentId(), definition.getDiagramResourceName());
        return stream != null ? stream : InputStream.nullInputStream();
    }

    @Override
    public List<TaskVO> getProcessHistory(String processInstanceId) {
        List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .list();
        return mapDoneTasks(tasks);
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

    private void ensureActiveProcess(String processInstanceId) {
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (processInstance == null) {
            throw new BusinessException("流程实例不存在或已结束");
        }
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
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户");
        }
        return String.valueOf(userId);
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
