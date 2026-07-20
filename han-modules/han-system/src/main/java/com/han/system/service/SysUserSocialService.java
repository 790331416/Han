package com.han.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.system.domain.po.SysUserSocialPo;
import com.han.system.mapper.SysUserSocialMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社交账号绑定服务（B 层）
 *
 * <p>唯一性规则（与 sys_user_social 唯一索引一致）：
 * <ul>
 *   <li>同一租户内一个第三方身份（provider+open_id）只能绑一个账号；</li>
 *   <li>一个账号在同一 provider 下只能绑一个第三方身份；</li>
 *   <li>同一第三方身份可在不同租户各绑一个账号（D7 租户隔离）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserSocialService {

    private final SysUserSocialMapper socialMapper;

    /**
     * 按 provider+openId 查询全部租户下的绑定
     */
    public List<SysUserSocialPo> listByProviderOpenId(String provider, String openId) {
        return socialMapper.selectList(new LambdaQueryWrapper<SysUserSocialPo>()
                .eq(SysUserSocialPo::getProvider, provider)
                .eq(SysUserSocialPo::getOpenId, openId)
                .orderByAsc(SysUserSocialPo::getId));
    }

    /**
     * 查询用户在某 provider 下的绑定
     */
    public SysUserSocialPo getByUserAndProvider(Long userId, String provider) {
        return socialMapper.selectOne(new LambdaQueryWrapper<SysUserSocialPo>()
                .eq(SysUserSocialPo::getUserId, userId)
                .eq(SysUserSocialPo::getProvider, provider)
                .last("LIMIT 1"));
    }

    /**
     * 查询用户全部社交绑定
     */
    public List<SysUserSocialPo> listByUser(Long userId) {
        return socialMapper.selectList(new LambdaQueryWrapper<SysUserSocialPo>()
                .eq(SysUserSocialPo::getUserId, userId)
                .orderByAsc(SysUserSocialPo::getId));
    }

    /**
     * 绑定社交账号（幂等：同用户同身份重复绑定只刷新昵称头像）
     *
     * @throws BusinessException 违反唯一性规则时
     */
    public void bind(Long userId, Long tenantId, String provider, String openId,
                     String accessToken, String nickname, String avatar) {
        if (userId == null || provider == null || provider.isBlank() || openId == null || openId.isBlank()) {
            throw new BusinessException("绑定参数不完整");
        }

        // 同租户内该第三方身份是否已绑定其他账号
        SysUserSocialPo sameIdentity = socialMapper.selectOne(new LambdaQueryWrapper<SysUserSocialPo>()
                .eq(SysUserSocialPo::getProvider, provider)
                .eq(SysUserSocialPo::getOpenId, openId)
                .eq(tenantId != null, SysUserSocialPo::getTenantId, tenantId)
                .isNull(tenantId == null, SysUserSocialPo::getTenantId)
                .last("LIMIT 1"));
        if (sameIdentity != null && !sameIdentity.getUserId().equals(userId)) {
            throw new BusinessException("该第三方账号已绑定本租户内其他用户");
        }

        // 该用户同 provider 是否已绑定其他第三方身份
        SysUserSocialPo sameUser = getByUserAndProvider(userId, provider);
        if (sameUser != null && !sameUser.getOpenId().equals(openId)) {
            throw new BusinessException("当前账号已绑定其他第三方账号，请先解绑");
        }

        SysUserSocialPo existing = sameIdentity != null ? sameIdentity : sameUser;
        if (existing != null) {
            existing.setUserId(userId);
            existing.setTenantId(tenantId);
            existing.setAccessToken(accessToken);
            existing.setNickname(nickname);
            existing.setAvatar(avatar);
            socialMapper.updateById(existing);
        } else {
            socialMapper.insert(SysUserSocialPo.builder()
                    .userId(userId)
                    .tenantId(tenantId)
                    .provider(provider)
                    .openId(openId)
                    .accessToken(accessToken)
                    .nickname(nickname)
                    .avatar(avatar)
                    .createTime(LocalDateTime.now())
                    .build());
        }
        log.info("用户[{}]绑定社交账号: provider={}, tenantId={}", userId, provider, tenantId);
    }

    /**
     * 解绑社交账号
     *
     * @return 是否有绑定被删除
     */
    public boolean unbind(Long userId, String provider) {
        int deleted = socialMapper.delete(new LambdaQueryWrapper<SysUserSocialPo>()
                .eq(SysUserSocialPo::getUserId, userId)
                .eq(SysUserSocialPo::getProvider, provider));
        if (deleted > 0) {
            log.info("用户[{}]解绑社交账号: provider={}", userId, provider);
        }
        return deleted > 0;
    }
}
