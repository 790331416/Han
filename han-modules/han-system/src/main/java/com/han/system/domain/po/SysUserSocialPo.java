package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 社交登录绑定持久化对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_social")
public class SysUserSocialPo {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private Long tenantId;
    private String provider;
    private String openId;
    private String accessToken;
    private String nickname;
    private String avatar;
    private String extra;
    private LocalDateTime createTime;
}
