package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.query.AiMcpServerQuery;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.service.IAiMcpServerService;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP server service implementation.
 */
@Service
@RequiredArgsConstructor
public class AiMcpServerServiceImpl extends AiServiceSupport implements IAiMcpServerService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiMcpServerMapper aiMcpServerMapper;

    @Override
    public PageResult<AiMcpServerPo> selectPage(AiMcpServerQuery query) {
        AiMcpServerQuery safeQuery = query != null ? query : new AiMcpServerQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<AiMcpServerPo> page = aiMcpServerMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(page.getRecords(), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public AiMcpServerPo selectById(Long mcpId) {
        return requireExisting(mcpId);
    }

    @Override
    public List<AiMcpServerPo> selectAll() {
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .eq(AiMcpServerPo::getStatus, STATUS_ENABLED)
                .orderByAsc(AiMcpServerPo::getServerName);
        applyTenantScope(wrapper);
        return aiMcpServerMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void insert(AiMcpServerPo server) {
        validateForCreate(server);
        ensureServerNameUnique(server.getServerName(), null);
        normalize(server);
        fillCreateAudit(server);
        aiMcpServerMapper.insert(server);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(AiMcpServerPo server) {
        if (server == null || server.getMcpId() == null) {
            throw new BusinessException("MCP服务ID不能为空");
        }
        AiMcpServerPo existing = requireExisting(server.getMcpId());
        copyEditableFields(server, existing);
        validateForCreate(existing);
        ensureServerNameUnique(existing.getServerName(), existing.getMcpId());
        normalize(existing);
        fillUpdateAudit(existing);
        aiMcpServerMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long mcpId) {
        requireExisting(mcpId);
        aiMcpServerMapper.deleteById(mcpId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String refreshTools(Long mcpId) {
        AiMcpServerPo server = requireExisting(mcpId);
        server.setTools(buildToolMetadata(server));
        fillUpdateAudit(server);
        aiMcpServerMapper.updateById(server);
        return "工具元数据已刷新";
    }

    private LambdaQueryWrapper<AiMcpServerPo> buildQueryWrapper(AiMcpServerQuery query) {
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .like(StringUtils.hasText(query.getServerName()), AiMcpServerPo::getServerName, query.getServerName())
                .eq(StringUtils.hasText(query.getTransportType()), AiMcpServerPo::getTransportType, query.getTransportType())
                .eq(StringUtils.hasText(query.getStatus()), AiMcpServerPo::getStatus, query.getStatus())
                .orderByDesc(AiMcpServerPo::getUpdateTime)
                .orderByDesc(AiMcpServerPo::getCreateTime);
        applyTenantScope(wrapper);
        return wrapper;
    }

    private void applyTenantScope(LambdaQueryWrapper<AiMcpServerPo> wrapper) {
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiMcpServerPo::getTenantId, tenantId);
        }
    }

    private AiMcpServerPo requireExisting(Long mcpId) {
        if (mcpId == null) {
            throw new BusinessException("MCP服务ID不能为空");
        }
        AiMcpServerPo server = aiMcpServerMapper.selectById(mcpId);
        if (server == null) {
            throw new BusinessException("MCP服务不存在");
        }
        Long tenantId = currentTenantId();
        if (tenantId != null && !tenantId.equals(server.getTenantId())) {
            throw new BusinessException("无权访问该MCP服务");
        }
        return server;
    }

    private void validateForCreate(AiMcpServerPo server) {
        if (server == null) {
            throw new BusinessException("MCP服务信息不能为空");
        }
        if (!StringUtils.hasText(server.getServerName())) {
            throw new BusinessException("服务名称不能为空");
        }
        if (!StringUtils.hasText(server.getTransportType())) {
            throw new BusinessException("传输类型不能为空");
        }
        if ("stdio".equals(server.getTransportType()) && !StringUtils.hasText(server.getCommand())) {
            throw new BusinessException("stdio 模式下命令不能为空");
        }
        if (!"stdio".equals(server.getTransportType()) && !StringUtils.hasText(server.getUrl())) {
            throw new BusinessException("远程模式下服务URL不能为空");
        }
        validateJson(server.getArgs(), "参数");
        validateJson(server.getEnvVars(), "环境变量");
        if (!STATUS_ENABLED.equals(server.getStatus()) && !STATUS_DISABLED.equals(server.getStatus())) {
            throw new BusinessException("MCP服务状态不合法");
        }
    }

    private void validateJson(String content, String fieldName) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        try {
            OBJECT_MAPPER.readTree(content);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(fieldName + "必须是合法JSON");
        }
    }

    private void ensureServerNameUnique(String serverName, Long excludeId) {
        LambdaQueryWrapper<AiMcpServerPo> wrapper = new LambdaQueryWrapper<AiMcpServerPo>()
                .eq(AiMcpServerPo::getServerName, serverName);
        Long tenantId = currentTenantId();
        if (tenantId != null) {
            wrapper.eq(AiMcpServerPo::getTenantId, tenantId);
        }
        if (excludeId != null) {
            wrapper.ne(AiMcpServerPo::getMcpId, excludeId);
        }
        if (aiMcpServerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("服务名称已存在");
        }
    }

    private void copyEditableFields(AiMcpServerPo source, AiMcpServerPo target) {
        target.setServerName(source.getServerName());
        target.setDescription(source.getDescription());
        target.setTransportType(source.getTransportType());
        target.setCommand(source.getCommand());
        target.setArgs(source.getArgs());
        target.setEnvVars(source.getEnvVars());
        target.setUrl(source.getUrl());
        target.setStatus(source.getStatus());
    }

    private void normalize(AiMcpServerPo server) {
        server.setServerName(trimToNull(server.getServerName()));
        server.setDescription(trimToNull(server.getDescription()));
        server.setTransportType(trimToNull(server.getTransportType()));
        server.setCommand(trimToNull(server.getCommand()));
        server.setArgs(StringUtils.hasText(server.getArgs()) ? server.getArgs().trim() : "[]");
        server.setEnvVars(StringUtils.hasText(server.getEnvVars()) ? server.getEnvVars().trim() : "{}");
        server.setUrl(trimToNull(server.getUrl()));
        server.setStatus(StringUtils.hasText(server.getStatus()) ? server.getStatus().trim() : STATUS_ENABLED);
        server.setTools(StringUtils.hasText(server.getTools()) ? server.getTools().trim() : "[]");
    }

    private String buildToolMetadata(AiMcpServerPo server) {
        List<Map<String, String>> tools = new ArrayList<>();
        tools.add(new LinkedHashMap<>(Map.of(
                "name", "health_check",
                "description", "检查 MCP 服务连接状态"
        )));
        if ("stdio".equals(server.getTransportType())) {
            tools.add(new LinkedHashMap<>(Map.of(
                    "name", "stdio_exec",
                    "description", "通过 stdio 方式执行工具调用"
            )));
        } else if ("streamable_http".equals(server.getTransportType())) {
            tools.add(new LinkedHashMap<>(Map.of(
                    "name", "http_stream_call",
                    "description", "通过 streamable HTTP 调用工具"
            )));
        } else {
            tools.add(new LinkedHashMap<>(Map.of(
                    "name", "sse_subscribe",
                    "description", "通过 SSE 订阅工具事件"
            )));
        }
        tools.add(new LinkedHashMap<>(Map.of(
                "name", "list_tools",
                "description", "读取当前服务登记的工具列表"
        )));
        try {
            return OBJECT_MAPPER.writeValueAsString(tools);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("生成工具元数据失败");
        }
    }

    private void fillCreateAudit(AiMcpServerPo server) {
        server.setTenantId(resolveTenantIdForWrite());
        server.setCreateBy(resolveOperator());
        server.setCreateTime(now());
        server.setUpdateBy(resolveOperator());
        server.setUpdateTime(now());
    }

    private void fillUpdateAudit(AiMcpServerPo server) {
        server.setUpdateBy(resolveOperator());
        server.setUpdateTime(now());
    }
}
