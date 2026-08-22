package com.han.open.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.han.common.core.domain.PageResult;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.AppGrantDetailVO;
import com.han.open.domain.vo.GrantApplyVO;
import com.han.open.domain.vo.OpenAppCredentialAdminVO;
import com.han.open.domain.vo.OpenAuthorizationRequestAdminVO;
import java.util.List;

/**
 * 应用授权服务接口
 */
public interface OpenAppAuthorizationService extends IService<OpenAppResourceGrantPo> {

    /** 管理端按当前租户分页查询授权申请。 */
    PageResult<OpenAuthorizationRequestAdminVO> listRequestPage(
            Long appId, Integer status, String environment, Integer pageNum, Integer pageSize);

    /** 管理端查询当前租户应用的分环境凭证（appId 为空时查询全部），禁止返回密钥或哈希。 */
    List<OpenAppCredentialAdminVO> listCredentials(Long appId);

    /**
     * 校验分环境客户端凭证。只返回运行时所需的非敏感字段，不返回明文密钥或哈希。
     */
    record CredentialContext(Long appId, String clientId, String environment) {
    }

    /**
     * 提交授权申请
     * @param applyVO 申请信息
     * @return 申请ID
     */
    Long submitGrantApply(GrantApplyVO applyVO);

    /**
     * 审核授权申请
     * @param requestId 申请ID
     * @param status 审核状态
     * @param reason 审核原因
     * @return 审核结果
     */
    boolean reviewGrantApply(Long requestId, Integer status, String reason);

    /**
     * 获取应用的授权列表
     * @param appId 应用ID
     * @return 授权列表
     */
    List<AppGrantDetailVO> listAppGrants(Long appId);

    /**
     * 获取应用已生效的授权资源列表
     * @param appId 应用ID
     * @return 资源ID列表
     */
    List<Long> listEffectiveResourceIds(Long appId);

    /**
     * 校验应用是否有权限访问指定资源
     * @param appId 应用ID
     * @param resourceId 资源ID
     * @param environment 环境
     * @param scope 访问Scope
     * @return 是否有权限
     */
    boolean hasPermission(Long appId, Long resourceId, String environment, String scope);

    /**
     * 撤销应用授权
     * @param grantId 授权ID
     * @param reason 撤销原因
     * @return 撤销结果
     */
    boolean revokeGrant(Long grantId, String reason);

    /**
     * 生成应用凭证
     * @param appId 应用ID
     * @param environment 环境
     * @return 凭证信息（包含明文Secret，仅返回一次）
     */
    AppCredentialVO generateCredential(Long appId, String environment);

    /**
     * 轮换应用凭证
     * @param credentialId 凭证ID
     * @return 新凭证信息（包含明文Secret，仅返回一次）
     */
    AppCredentialVO rotateCredential(Long credentialId);

    /**
     * 校验ClientId和Secret是否有效
     * @param clientId 客户端ID
     * @param clientSecret 客户端密钥
     * @return 应用ID，无效返回null
     */
    Long validateCredential(String clientId, String clientSecret);

    /**
     * 校验分环境客户端凭证并返回环境上下文，供 OAuth2 Token 端点使用。
     */
    CredentialContext validateCredentialContext(String clientId, String clientSecret);

    /**
     * 查找租户内、指定环境和 Scope 对应的已发布有效授权。
     * 返回空字符串表示授权存在但未配置额外数据范围，返回 null 表示没有有效授权。
     */
    String resolveAuthorizedDataScope(Long tenantId, Long appId, String environment, String scope);

    /**
     * 查找指定资源编码对应的已发布资源，并只校验该资源的有效授权。
     * 用于避免多个资源共用 Scope 时发生越权。
     */
    String resolveAuthorizedDataScope(Long tenantId, Long appId, String environment,
                                      String scope, String resourceCode);
}
