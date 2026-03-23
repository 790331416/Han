package com.han.system.domain.po;

import com.baomidou.mybatisplus.annotation.TableName;
import com.han.common.mybatis.domain.entity.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 用户通知已读状态持久化对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice_read")
public class SysNoticeReadPo extends TenantEntity {

    /**
     * 通知 ID。
     */
    private Long noticeId;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 已读时间。
     */
    private LocalDateTime readTime;
}
