package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.ai.domain.po.AiMcpServerPo;
import com.han.ai.domain.query.AiMcpServerQuery;
import com.han.ai.mapper.AiMcpServerMapper;
import com.han.ai.security.AiUrlSecurityValidator;
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
    private final AiMcpClientService aiMcpClientService;
    private final AiUrlSecurityValidator urlSecurityValidator;

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
        // 真连 MCP server 拉取 tools/list（name/description/inputSchema）入库，替换手填元数据
        List<AiMcpClientService.McpTool> tools = aiMcpClientService.listTools(server);
        List<Map<String, Object>> metadata = new ArrayList<>();
        for (AiMcpClientService.McpTool tool : tools) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", tool.name());
            item.put("description", tool.description());
            item.put("inputSchema", tool.inputSchema());
            metadata.add(item);
        }
        try {
            server.setTools(OBJECT_MAPPER.writeValueAsString(metadata));
        } catch (JsonProcessingException ex) {
            throw new BusinessException("工具元数据序列化失败");
        }
        fillUpdateAudit(server);
        aiMcpServerMapper.updateById(server);
        return "已从 MCP 服务拉取 " + tools.size() + " 个工具";
    }

    @Override
    public String testConnection(Long mcpId) {
        AiMcpServerPo server = requireExisting(mcpId);
        // 真连 initialize + tools/list 但不落库；失败时透传客户端/SSRF 校验的可诊断文案（G1-5）
        List<AiMcpClientService.McpTool> tools = aiMcpClientService.listTools(server);
        return "连接成功，检测到 " + tools.size() + " 个工具";
    }

    @Override
    public List<Map<String, Object>> listToolMetadata(Long mcpId) {
        AiMcpServerPo server = requireExisting(mcpId);
        if (!StringUtils.hasText(server.getTools())) {
            return List.of();
        }
        try {
            List<Map<String, Object>> tools = OBJECT_MAPPER.readValue(server.getTools(),
                    new TypeReference<List<Map<String, Object>>>() {});
            return tools != null ? tools : List.of();
        } catch (JsonProcessingException ex) {
            // 库内元数据非法（历史手填脏数据）按空清单返回，引导前端提示先刷新工具
            return List.of();
        }
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
        // stdio 处置（决策 D4）：连接层已拒绝执行，保存层同步拦截，消除「收了不能用」；字段保留不删
        if ("stdio".equals(server.getTransportType())) {
            throw new BusinessException("stdio 传输暂不可用：容器环境不支持本地进程管理，请选择 SSE 或 Streamable HTTP 远程传输");
        }
        if (!StringUtils.hasText(server.getUrl())) {
            throw new BusinessException("远程模式下服务URL不能为空");
        }
        // SSRF 防护（G1-12）：保存路径即校验，内网/环回等地址默认拒绝，可配白名单放行
        urlSecurityValidator.validate(server.getUrl(), "MCP服务");
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

    /**
     * 合并请求体到库内现值：未提交的字段保持原样（见 {@link AiServiceSupport#copyIfPresent}）。
     */
    private void copyEditableFields(AiMcpServerPo source, AiMcpServerPo target) {
        copyIfPresent(source.getServerName(), target::setServerName);
        copyIfPresent(source.getDescription(), target::setDescription);
        copyIfPresent(source.getTransportType(), target::setTransportType);
        copyIfPresent(source.getCommand(), target::setCommand);
        copyIfPresent(source.getArgs(), target::setArgs);
        copyIfPresent(source.getEnvVars(), target::setEnvVars);
        copyIfPresent(source.getUrl(), target::setUrl);
        copyIfPresent(source.getStatus(), target::setStatus);
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
