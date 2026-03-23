package com.han.system.controller.inner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.R;
import com.han.common.security.annotation.InnerAuth;
import com.han.system.domain.po.SysUserSocialPo;
import com.han.system.mapper.SysUserSocialMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 社交登录 - I层（内部接口控制器）
 */
@InnerAuth
@RestController("innerSocialController")
@RequestMapping("/inner/system")
@RequiredArgsConstructor
public class ISocialController {

    private final SysUserSocialMapper socialMapper;

    @GetMapping("/social/bindUser")
    public R<Long> getSocialBindUserId(@RequestParam("provider") String provider,
                                        @RequestParam("openId") String openId) {
        SysUserSocialPo po = socialMapper.selectOne(
                new LambdaQueryWrapper<SysUserSocialPo>()
                        .eq(SysUserSocialPo::getProvider, provider)
                        .eq(SysUserSocialPo::getOpenId, openId)
                        .last("LIMIT 1"));
        return po != null ? R.ok(po.getUserId()) : R.ok(null);
    }

    @PostMapping("/social/bind")
    public R<Void> bindSocialUser(@RequestParam("userId") Long userId,
                                   @RequestParam("provider") String provider,
                                   @RequestParam("openId") String openId,
                                   @RequestParam(value = "accessToken", required = false) String accessToken,
                                   @RequestParam(value = "nickname", required = false) String nickname,
                                   @RequestParam(value = "avatar", required = false) String avatar) {
        // 检查是否已绑定
        Long count = socialMapper.selectCount(
                new LambdaQueryWrapper<SysUserSocialPo>()
                        .eq(SysUserSocialPo::getProvider, provider)
                        .eq(SysUserSocialPo::getOpenId, openId));
        if (count > 0) {
            // 更新已有绑定
            SysUserSocialPo existing = socialMapper.selectOne(
                    new LambdaQueryWrapper<SysUserSocialPo>()
                            .eq(SysUserSocialPo::getProvider, provider)
                            .eq(SysUserSocialPo::getOpenId, openId)
                            .last("LIMIT 1"));
            existing.setUserId(userId);
            existing.setAccessToken(accessToken);
            existing.setNickname(nickname);
            existing.setAvatar(avatar);
            socialMapper.updateById(existing);
        } else {
            SysUserSocialPo po = SysUserSocialPo.builder()
                    .userId(userId)
                    .provider(provider)
                    .openId(openId)
                    .accessToken(accessToken)
                    .nickname(nickname)
                    .avatar(avatar)
                    .createTime(LocalDateTime.now())
                    .build();
            socialMapper.insert(po);
        }
        return R.ok();
    }
}
