package com.han.open.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.HanIdUtil;
import com.han.common.core.util.PasswordUtil;
import com.han.open.converter.OpenAppConverter;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.domain.vo.OpenAppCredentialVO;
import com.han.open.mapper.OpenAppMapper;
import com.han.open.service.IOpenAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 开放平台应用服务实现。
 */
@Service
@RequiredArgsConstructor
public class OpenAppServiceImpl implements IOpenAppService {

    private static final int STATUS_ENABLED = 0;
    private static final int STATUS_DISABLED = 1;
    private static final int DEFAULT_ACCESS_TOKEN_TTL = 7200;
    private static final int DEFAULT_REFRESH_TOKEN_TTL = 604800;
    private static final int DEFAULT_REQUIRE_PKCE = 0;
    private static final int DEFAULT_AUTO_APPROVE = 0;
    private static final String DEFAULT_APP_TYPE = "web";
    private static final List<String> ALLOWED_APP_TYPES = List.of("web", "mobile", "server");
    private static final String DEFAULT_GRANT_TYPES = "authorization_code,refresh_token";
    private static final String DEFAULT_SCOPES = "openid,profile";
    private static final String APP_KEY_PREFIX = "app_";

    private final OpenAppMapper openAppMapper;
    private final OpenAppConverter openAppConverter;

    @Override
    public PageResult<OpenAppVO> selectPage(OpenAppQuery query) {
        OpenAppQuery safeQuery = query != null ? query : new OpenAppQuery();
        int pageNum = normalizePageNum(safeQuery.getPageNum());
        int pageSize = normalizePageSize(safeQuery.getPageSize());
        Page<OpenAppPo> page = openAppMapper.selectPage(new Page<>(pageNum, pageSize), buildQueryWrapper(safeQuery));
        return PageResult.of(openAppConverter.toVOList(page.getRecords()), page.getTotal(), pageNum, pageSize);
    }

    @Override
    public OpenAppVO selectVoById(Long appId) {
        return openAppConverter.toVO(requireExisting(appId));
    }

    @Override
    public List<OpenAppDTO> selectListScope(OpenAppQuery query) {
        return selectList(query);
    }

    @Override
    public List<OpenAppDTO> selectList(OpenAppQuery query) {
        return openAppMapper.selectList(buildQueryWrapper(query)).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public OpenAppDTO selectById(Long id) {
        return toDto(requireExisting(id));
    }

    @Override
    public List<OpenAppDTO> selectByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .in(OpenAppPo::getId, ids)
                .orderByDesc(OpenAppPo::getCreateTime);
        return openAppMapper.selectList(wrapper).stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(OpenAppDTO dto) {
        createWithCredentials(dto);
        return 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OpenAppCredentialVO createWithCredentials(OpenAppDTO dto) {
        OpenAppPo po = openAppConverter.toPo(dto);
        if (po == null) {
            throw new BusinessException("应用信息不能为空");
        }
        normalizeForCreate(po);
        validateForSave(po, null);
        po.setAppKey(generateAppKey());
        String appSecret = generateAppSecret();
        po.setAppSecret(PasswordUtil.encode(appSecret));
        openAppMapper.insert(po);
        return new OpenAppCredentialVO(po.getId(), po.getAppKey(), appSecret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(OpenAppDTO dto) {
        Long appId = dto != null ? dto.getAppId() : null;
        if (appId == null) {
            throw new BusinessException("应用ID不能为空");
        }
        OpenAppPo existing = requireExisting(appId);
        openAppConverter.updatePo(dto, existing);
        normalizeForUpdate(existing);
        validateForSave(existing, existing.getId());
        return openAppMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        requireExisting(id);
        return openAppMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(List<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .in(OpenAppPo::getId, ids);
        return openAppMapper.delete(wrapper);
    }

    @Override
    public OpenAppVO getAppByAppKey(String appKey) {
        if (!StringUtils.hasText(appKey)) {
            return null;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, appKey.trim())
                .last("LIMIT 1"));
        return po != null ? openAppConverter.toVO(po) : null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetAppSecret(Long appId) {
        OpenAppPo existing = requireExisting(appId);
        String newSecret = generateAppSecret();
        OpenAppPo update = new OpenAppPo();
        update.setId(existing.getId());
        update.setAppSecret(PasswordUtil.encode(newSecret));
        openAppMapper.updateById(update);
        return newSecret;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long appId, Integer status) {
        requireExisting(appId);
        validateStatus(status);
        OpenAppPo update = new OpenAppPo();
        update.setId(appId);
        update.setStatus(status);
        openAppMapper.updateById(update);
    }

    @Override
    public boolean validateClient(String clientId, String clientSecret) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)) {
            return false;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, clientId.trim())
                .eq(OpenAppPo::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (po == null) {
            return false;
        }
        if (PasswordUtil.matches(clientSecret.trim(), po.getAppSecret())) {
            return true;
        }
        // 兼容已落库的明文旧密钥：首次成功使用后升级为哈希，不影响存量第三方接入。
        if (MessageDigest.isEqual(clientSecret.trim().getBytes(StandardCharsets.UTF_8),
                po.getAppSecret().getBytes(StandardCharsets.UTF_8))) {
            OpenAppPo update = new OpenAppPo();
            update.setId(po.getId());
            update.setAppSecret(PasswordUtil.encode(clientSecret.trim()));
            openAppMapper.updateById(update);
            return true;
        }
        return false;
    }

    @Override
    public boolean validateRedirectUri(String clientId, String redirectUri) {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(redirectUri)) {
            return false;
        }
        OpenAppPo po = openAppMapper.selectOne(new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppKey, clientId.trim())
                .eq(OpenAppPo::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        if (po == null || !StringUtils.hasText(po.getRedirectUris())) {
            return false;
        }
        String target = redirectUri.trim();
        return openAppConverter.stringToList(po.getRedirectUris()).stream()
                .map(String::trim)
                .anyMatch(target::equals);
    }

    private LambdaQueryWrapper<OpenAppPo> buildQueryWrapper(OpenAppQuery query) {
        OpenAppQuery safeQuery = query != null ? query : new OpenAppQuery();
        String appType = resolveAppType(safeQuery);
        return new LambdaQueryWrapper<OpenAppPo>()
                .like(StringUtils.hasText(safeQuery.getAppName()), OpenAppPo::getAppName, safeQuery.getAppName())
                .eq(StringUtils.hasText(appType), OpenAppPo::getAppType, appType)
                .eq(safeQuery.getStatus() != null, OpenAppPo::getStatus, safeQuery.getStatus())
                .orderByDesc(OpenAppPo::getUpdateTime)
                .orderByDesc(OpenAppPo::getCreateTime);
    }

    private String resolveAppType(OpenAppQuery query) {
        if (StringUtils.hasText(query.getAppType())) {
            return query.getAppType().trim();
        }
        if (query.getBase() != null && StringUtils.hasText(query.getBase().getAppType())) {
            return query.getBase().getAppType().trim();
        }
        return null;
    }

    private OpenAppPo requireExisting(Long appId) {
        if (appId == null) {
            throw new BusinessException("应用ID不能为空");
        }
        OpenAppPo po = openAppMapper.selectById(appId);
        if (po == null) {
            throw new BusinessException("应用不存在");
        }
        return po;
    }

    private OpenAppDTO toDto(OpenAppPo po) {
        if (po == null) {
            return null;
        }
        OpenAppDTO dto = new OpenAppDTO();
        dto.setBase(po);
        dto.setRedirectUris(openAppConverter.stringToList(po.getRedirectUris()));
        dto.setScopes(openAppConverter.stringToList(po.getScopes()));
        dto.setGrantTypes(openAppConverter.stringToList(po.getGrantTypes()));
        return dto;
    }

    private void normalizeForCreate(OpenAppPo po) {
        po.setAppName(trimToNull(po.getAppName()));
        po.setAppIcon(trimToNull(po.getAppIcon()));
        po.setAppDesc(trimToNull(po.getAppDesc()));
        po.setAppType(StringUtils.hasText(po.getAppType()) ? po.getAppType().trim() : DEFAULT_APP_TYPE);
        po.setLogoutUri(trimToNull(po.getLogoutUri()));
        po.setRedirectUris(normalizeCommaSeparated(po.getRedirectUris()));
        po.setScopes(StringUtils.hasText(po.getScopes()) ? normalizeCommaSeparated(po.getScopes()) : DEFAULT_SCOPES);
        po.setSchoolScope(normalizeSchoolScope(po.getSchoolScope()));
        po.setGrantTypes(StringUtils.hasText(po.getGrantTypes()) ? normalizeCommaSeparated(po.getGrantTypes())
                : ("server".equals(po.getAppType()) ? "client_credentials" : DEFAULT_GRANT_TYPES));
        po.setAccessTokenTtl(po.getAccessTokenTtl() != null ? po.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL);
        po.setRefreshTokenTtl(po.getRefreshTokenTtl() != null ? po.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL);
        po.setRequirePkce(po.getRequirePkce() != null ? po.getRequirePkce() : DEFAULT_REQUIRE_PKCE);
        po.setAutoApprove(po.getAutoApprove() != null ? po.getAutoApprove() : DEFAULT_AUTO_APPROVE);
        po.setStatus(po.getStatus() != null ? po.getStatus() : STATUS_ENABLED);
        po.setContactName(trimToNull(po.getContactName()));
        po.setContactPhone(trimToNull(po.getContactPhone()));
        po.setContactEmail(trimToNull(po.getContactEmail()));
        po.setRemark(trimToNull(po.getRemark()));
    }

    private void normalizeForUpdate(OpenAppPo po) {
        po.setAppName(trimToNull(po.getAppName()));
        po.setAppIcon(trimToNull(po.getAppIcon()));
        po.setAppDesc(trimToNull(po.getAppDesc()));
        po.setAppType(StringUtils.hasText(po.getAppType()) ? po.getAppType().trim() : DEFAULT_APP_TYPE);
        po.setLogoutUri(trimToNull(po.getLogoutUri()));
        po.setRedirectUris(normalizeCommaSeparated(po.getRedirectUris()));
        po.setScopes(StringUtils.hasText(po.getScopes()) ? normalizeCommaSeparated(po.getScopes()) : DEFAULT_SCOPES);
        po.setSchoolScope(normalizeSchoolScope(po.getSchoolScope()));
        po.setGrantTypes(StringUtils.hasText(po.getGrantTypes()) ? normalizeCommaSeparated(po.getGrantTypes())
                : ("server".equals(po.getAppType()) ? "client_credentials" : DEFAULT_GRANT_TYPES));
        po.setAccessTokenTtl(po.getAccessTokenTtl() != null ? po.getAccessTokenTtl() : DEFAULT_ACCESS_TOKEN_TTL);
        po.setRefreshTokenTtl(po.getRefreshTokenTtl() != null ? po.getRefreshTokenTtl() : DEFAULT_REFRESH_TOKEN_TTL);
        po.setRequirePkce(po.getRequirePkce() != null ? po.getRequirePkce() : DEFAULT_REQUIRE_PKCE);
        po.setAutoApprove(po.getAutoApprove() != null ? po.getAutoApprove() : DEFAULT_AUTO_APPROVE);
        po.setStatus(po.getStatus() != null ? po.getStatus() : STATUS_ENABLED);
        po.setContactName(trimToNull(po.getContactName()));
        po.setContactPhone(trimToNull(po.getContactPhone()));
        po.setContactEmail(trimToNull(po.getContactEmail()));
        po.setRemark(trimToNull(po.getRemark()));
    }

    private void validateForSave(OpenAppPo po, Long currentId) {
        if (!StringUtils.hasText(po.getAppName())) {
            throw new BusinessException("应用名称不能为空");
        }
        if (!StringUtils.hasText(po.getAppType())) {
            throw new BusinessException("应用类型不能为空");
        }
        if (!ALLOWED_APP_TYPES.contains(po.getAppType())) {
            throw new BusinessException("应用类型不支持");
        }
        if (po.getAccessTokenTtl() == null || po.getAccessTokenTtl() < 60) {
            throw new BusinessException("AccessToken 有效期不能小于 60 秒");
        }
        if (po.getRefreshTokenTtl() == null || po.getRefreshTokenTtl() < 60) {
            throw new BusinessException("RefreshToken 有效期不能小于 60 秒");
        }
        if (hasEducationDirectoryScope(po.getScopes()) && !StringUtils.hasText(po.getSchoolScope())) {
            throw new BusinessException("授权教师、学生或设备目录时必须指定学校范围");
        }
        validateStatus(po.getStatus());
        ensureAppNameUnique(po.getAppName(), currentId);
    }

    private void ensureAppNameUnique(String appName, Long currentId) {
        LambdaQueryWrapper<OpenAppPo> wrapper = new LambdaQueryWrapper<OpenAppPo>()
                .eq(OpenAppPo::getAppName, appName);
        if (currentId != null) {
            wrapper.ne(OpenAppPo::getId, currentId);
        }
        if (openAppMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("应用名称已存在");
        }
    }

    private void validateStatus(Integer status) {
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new BusinessException("应用状态不合法");
        }
    }

    private int normalizePageNum(Integer pageNum) {
        return pageNum == null || pageNum < 1 ? 1 : pageNum;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String normalizeCommaSeparated(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return openAppConverter.stringToList(value).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String normalizeSchoolScope(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            return openAppConverter.stringToLongList(value).stream()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(","));
        } catch (NumberFormatException e) {
            throw new BusinessException("学校范围必须是有效的学校ID列表");
        }
    }

    private static boolean hasEducationDirectoryScope(String scopes) {
        return scopes != null && java.util.Arrays.stream(scopes.split(","))
                .map(String::trim)
                .anyMatch(item -> item.equals("edu.teacher.read")
                        || item.equals("edu.student.read")
                        || item.equals("edu.device.read"));
    }

    private String generateAppKey() {
        return APP_KEY_PREFIX + HanIdUtil.uuid();
    }

    private String generateAppSecret() {
        return PasswordUtil.generatePassword(20);
    }
}
