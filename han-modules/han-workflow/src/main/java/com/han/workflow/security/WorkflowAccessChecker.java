package com.han.workflow.security;

import com.han.common.core.exception.BusinessException;
import com.han.common.core.exception.ForbiddenException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.common.security.util.DataOwnerUtil;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.TaskService;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 工作流数据权限校验：任务归属、流程实例归属与租户隔离。
 *
 * <p>归属判定复用 {@link DataOwnerUtil}；租户语义与 MyBatis 侧
 * {@code HanTenantLineHandler} 保持一致——没有租户上下文时不做租户收敛。
 * Flowable 中租户为空的数据是启用租户隔离之前写入的存量数据，按平台公共数据处理，
 * 不会因为本次改造从列表里消失。
 */
@Component
@RequiredArgsConstructor
public class WorkflowAccessChecker {

    private final TaskService taskService;

    /**
     * 当前登录用户 ID（Flowable 侧统一用字符串保存）
     */
    public String requiredCurrentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BusinessException("未获取到当前用户");
        }
        return String.valueOf(userId);
    }

    public boolean isAdmin() {
        return SecurityContextHolder.isAdmin();
    }

    /**
     * 当前租户 ID；不在租户上下文中时返回 {@code null}，调用方据此跳过租户过滤
     */
    public String currentTenantId() {
        Long tenantId = SecurityContextHolder.getTenantId();
        return tenantId == null ? null : String.valueOf(tenantId);
    }

    /**
     * 校验 Flowable 数据的租户归属，越租户操作直接 403
     */
    public void checkTenant(String dataTenantId) {
        if (SecurityContextHolder.getTenantId() == null || !StringUtils.hasText(dataTenantId)) {
            return;
        }
        Long dataTenant = parseId(dataTenantId);
        if (dataTenant == null) {
            throw new ForbiddenException("无权操作其他租户数据");
        }
        DataOwnerUtil.checkTenant(dataTenant);
    }

    /**
     * 校验流程实例归属：仅发起人本人或超级管理员可操作
     *
     * @param action 用于错误提示的动作名，例如「终止」
     */
    public void checkInstanceOwner(String tenantId, String startUserId, String action) {
        checkTenant(tenantId);
        if (isAdmin()) {
            return;
        }
        String currentUserId = requiredCurrentUserId();
        if (currentUserId.equals(trim(startUserId))) {
            return;
        }
        Long owner = parseId(startUserId);
        if (owner != null) {
            DataOwnerUtil.checkOwner(owner);
            return;
        }
        throw new ForbiddenException("无权" + action + "他人发起的流程");
    }

    /**
     * 办理：必须是当前办理人；办理人为空时必须是候选人（由调用方先签收再办理）
     */
    public void checkCanComplete(Task task) {
        checkTenant(task.getTenantId());
        if (isAdmin()) {
            return;
        }
        if (isAssignee(task)) {
            return;
        }
        if (!StringUtils.hasText(task.getAssignee()) && isCandidate(task)) {
            return;
        }
        throw new ForbiddenException("无权办理他人的任务");
    }

    /**
     * 转办 / 委派：必须是当前办理人或任务所有人
     */
    public void checkCanTransfer(Task task, String action) {
        checkTenant(task.getTenantId());
        if (isAdmin()) {
            return;
        }
        if (isAssignee(task) || isOwner(task)) {
            return;
        }
        throw new ForbiddenException("无权" + action + "他人的任务");
    }

    /**
     * 签收：任务未被他人占用，且当前用户在候选人 / 候选组中
     */
    public void checkCanClaim(Task task) {
        checkTenant(task.getTenantId());
        if (StringUtils.hasText(task.getAssignee()) && !isAssignee(task)) {
            throw new ForbiddenException("任务已被他人签收");
        }
        if (isAdmin() || isCandidate(task)) {
            return;
        }
        throw new ForbiddenException("当前用户不在该任务的候选人范围内");
    }

    /**
     * 取消签收：必须是当前办理人，且任务上仍有候选人 / 候选组可以重新签收。
     *
     * <p>没有候选人时归还会让任务同时脱离所有人的待办且无从捡回，流程将永久卡死，
     * 因此这里直接拒绝。
     */
    public void checkCanUnclaim(Task task) {
        checkTenant(task.getTenantId());
        if (!isAdmin() && !isAssignee(task)) {
            throw new ForbiddenException("无权取消他人任务的签收");
        }
        if (!hasCandidate(task.getId())) {
            throw new BusinessException("该任务没有候选人或候选组，取消签收后将无人可以签收，已阻止操作");
        }
    }

    /**
     * 当前用户是否为任务的候选人（含候选组，候选组按角色标识与角色 ID 匹配）
     */
    public boolean isCandidate(Task task) {
        LoginUser user = SecurityContextHolder.getLoginUser();
        if (user == null || user.getUserId() == null) {
            return false;
        }
        String currentUserId = String.valueOf(user.getUserId());
        Set<String> groups = candidateGroupIdentities(user);
        for (IdentityLink link : identityLinks(task.getId())) {
            if (!IdentityLinkType.CANDIDATE.equals(link.getType())) {
                continue;
            }
            if (currentUserId.equals(link.getUserId())) {
                return true;
            }
            if (StringUtils.hasText(link.getGroupId()) && groups.contains(link.getGroupId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 任务上是否存在候选人或候选组
     */
    public boolean hasCandidate(String taskId) {
        for (IdentityLink link : identityLinks(taskId)) {
            if (!IdentityLinkType.CANDIDATE.equals(link.getType())) {
                continue;
            }
            if (StringUtils.hasText(link.getUserId()) || StringUtils.hasText(link.getGroupId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 当前用户可用于匹配候选组的身份集合：角色标识与角色 ID
     */
    public Set<String> candidateGroupIdentities(LoginUser user) {
        Set<String> identities = new LinkedHashSet<>();
        if (user == null) {
            return identities;
        }
        if (user.getRoleKeys() != null) {
            user.getRoleKeys().stream().filter(StringUtils::hasText).forEach(identities::add);
        }
        if (user.getRoleIds() != null) {
            user.getRoleIds().stream().filter(java.util.Objects::nonNull).map(String::valueOf).forEach(identities::add);
        }
        return identities;
    }

    private boolean isAssignee(Task task) {
        return requiredCurrentUserId().equals(trim(task.getAssignee()));
    }

    private boolean isOwner(Task task) {
        return requiredCurrentUserId().equals(trim(task.getOwner()));
    }

    private List<IdentityLink> identityLinks(String taskId) {
        List<IdentityLink> links = taskService.getIdentityLinksForTask(taskId);
        return links == null ? List.of() : links;
    }

    private Long parseId(String value) {
        String trimmed = trim(value);
        if (trimmed == null) {
            return null;
        }
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
