package com.han.open.converter;

import com.han.open.domain.po.OpenAppCredentialPo;
import com.han.open.domain.po.OpenAppResourceGrantPo;
import com.han.open.domain.po.OpenAuthorizationRequestPo;
import com.han.open.domain.vo.AppCredentialVO;
import com.han.open.domain.vo.AppGrantDetailVO;
import com.han.open.domain.vo.OpenAppCredentialAdminVO;
import com.han.open.domain.vo.OpenAuthorizationRequestAdminVO;

/**
 * 应用授权对象转换器。
 *
 * <p>凭证转换只映射 clientId 等非敏感字段；明文 secret 只有生成/轮换的调用方
 * 通过显式参数传入，哈希字段永远不会进入 VO。</p>
 */
public final class OpenAppAuthorizationConverter {

    private OpenAppAuthorizationConverter() {
    }

    public static OpenAuthorizationRequestAdminVO toRequestAdminVO(OpenAuthorizationRequestPo source) {
        if (source == null) {
            return null;
        }
        OpenAuthorizationRequestAdminVO target = new OpenAuthorizationRequestAdminVO();
        target.setId(source.getId());
        target.setRequestId(source.getId());
        target.setAppId(source.getAppId());
        target.setGrantId(source.getGrantId());
        target.setEnvironment(source.getEnvironment());
        target.setRequestType(source.getRequestType());
        target.setStatus(source.getStatus());
        target.setRequestData(source.getRequestData());
        target.setReason(source.getReason());
        target.setReviewReason(source.getReviewReason());
        target.setApplicantId(source.getApplicantId());
        target.setReviewerId(source.getReviewerId());
        target.setReviewTime(source.getReviewTime());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public static AppGrantDetailVO toGrantDetailVO(OpenAppResourceGrantPo source) {
        if (source == null) {
            return null;
        }
        AppGrantDetailVO target = new AppGrantDetailVO();
        target.setId(source.getId());
        target.setAppId(source.getAppId());
        target.setResourceId(source.getResourceId());
        target.setVersionId(source.getVersionId());
        target.setScopes(source.getScopes());
        target.setDataScope(source.getDataScope());
        target.setQuota(source.getQuota());
        target.setExpiresAt(source.getExpiresAt());
        target.setStatus(source.getStatus());
        target.setApplyReason(source.getApplyReason());
        target.setReviewReason(source.getReviewReason());
        target.setReviewTime(source.getReviewTime());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public static OpenAppCredentialAdminVO toCredentialAdminVO(OpenAppCredentialPo source) {
        if (source == null) {
            return null;
        }
        OpenAppCredentialAdminVO target = new OpenAppCredentialAdminVO();
        target.setId(source.getId());
        target.setCredentialId(source.getId());
        target.setAppId(source.getAppId());
        target.setEnvironment(source.getEnvironment());
        target.setClientId(source.getClientId());
        target.setStatus(source.getStatus());
        target.setRotatedAt(source.getRotatedAt());
        target.setExpireAt(source.getExpireAt());
        target.setCreateTime(source.getCreateTime());
        return target;
    }

    public static AppCredentialVO toCredentialVO(OpenAppCredentialPo source, String clientSecret) {
        if (source == null) {
            return null;
        }
        AppCredentialVO target = new AppCredentialVO();
        target.setId(source.getId());
        target.setAppId(source.getAppId());
        target.setEnvironment(source.getEnvironment());
        target.setClientId(source.getClientId());
        target.setClientSecret(clientSecret);
        target.setStatus(source.getStatus());
        target.setRotatedAt(source.getRotatedAt());
        target.setExpireAt(source.getExpireAt());
        target.setCreateTime(source.getCreateTime());
        return target;
    }
}
