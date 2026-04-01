package com.han.system.domain.vo;

import com.han.system.domain.po.SysNoticePo;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知铃铛最新通知视图对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class NoticeLatestVo extends SysNoticePo {

    /**
     * 当前用户是否已读。
     */
    private Boolean read;

    /**
     * 当前用户已读时间。
     */
    private LocalDateTime readTime;
}
