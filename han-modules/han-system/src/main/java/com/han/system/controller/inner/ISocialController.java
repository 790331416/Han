package com.han.system.controller.inner;

import com.han.api.system.domain.SocialBindingVO;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.SysUserSocialPo;
import com.han.system.service.SysUserSocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 社交登录 - I层（内部接口控制器）
 */
@InnerAuth
@RestController("innerSocialController")
@RequestMapping("/inner/system")
@RequiredArgsConstructor
public class ISocialController {

    private final SysUserSocialService socialService;

    /**
     * 查询社交账号绑定的系统用户ID
     *
     * @deprecated 唯一键已按租户隔离，跨租户可能命中多条；保留兼容旧调用方，取最早一条。
     */
    @Deprecated
    @GetMapping("/social/bindUser")
    public R<Long> getSocialBindUserId(@RequestParam("provider") String provider,
                                        @RequestParam("openId") String openId) {
        return R.ok(socialService.listByProviderOpenId(provider, openId).stream()
                .map(SysUserSocialPo::getUserId)
                .findFirst()
                .orElse(null));
    }

    /**
     * 查询社交账号在所有租户下的绑定列表
     */
    @GetMapping("/social/bindings")
    public R<List<SocialBindingVO>> listSocialBindings(@RequestParam("provider") String provider,
                                                        @RequestParam("openId") String openId) {
        return R.ok(socialService.listByProviderOpenId(provider, openId).stream().map(this::toVo).toList());
    }

    /**
     * 查询用户在某 provider 下的绑定
     */
    @GetMapping("/social/binding")
    public R<SocialBindingVO> getUserSocialBinding(@RequestParam("userId") Long userId,
                                                    @RequestParam("provider") String provider) {
        SysUserSocialPo po = socialService.getByUserAndProvider(userId, provider);
        return R.ok(po != null ? toVo(po) : null);
    }

    /**
     * 查询用户全部社交绑定
     */
    @GetMapping("/social/userBindings")
    public R<List<SocialBindingVO>> listUserSocialBindings(@RequestParam("userId") Long userId) {
        return R.ok(socialService.listByUser(userId).stream().map(this::toVo).toList());
    }

    /**
     * 绑定社交账号
     */
    @PostMapping("/social/bind")
    public R<Void> bindSocialUser(@RequestParam("userId") Long userId,
                                   @RequestParam(value = "tenantId", required = false) Long tenantId,
                                   @RequestParam("provider") String provider,
                                   @RequestParam("openId") String openId,
                                   @RequestParam(value = "accessToken", required = false) String accessToken,
                                   @RequestParam(value = "nickname", required = false) String nickname,
                                   @RequestParam(value = "avatar", required = false) String avatar) {
        socialService.bind(userId, tenantId, provider, openId, accessToken, nickname, avatar);
        return R.ok();
    }

    /**
     * 解绑社交账号
     */
    @PostMapping("/social/unbind")
    public R<Void> unbindSocialUser(@RequestParam("userId") Long userId,
                                     @RequestParam("provider") String provider) {
        socialService.unbind(userId, provider);
        return R.ok();
    }

    private SocialBindingVO toVo(SysUserSocialPo po) {
        return SocialBindingVO.builder()
                .id(po.getId())
                .userId(po.getUserId())
                .tenantId(po.getTenantId())
                .provider(po.getProvider())
                .openId(po.getOpenId())
                .nickname(po.getNickname())
                .avatar(po.getAvatar())
                .createTime(po.getCreateTime())
                .build();
    }
}
