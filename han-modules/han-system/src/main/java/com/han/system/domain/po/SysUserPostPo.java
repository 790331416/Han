package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户-岗位关联表（物理删除，无继承）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_user_post")
public class SysUserPostPo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long userId;

    /** 岗位ID */
    private Long postId;
}
