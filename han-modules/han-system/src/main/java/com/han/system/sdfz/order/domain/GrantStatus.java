package com.han.system.sdfz.order.domain;

/**
 * 授权台账状态。
 */
public enum GrantStatus {

    /** 已判定应授权，尚未写进三课堂。 */
    PENDING,

    /** 已在 tb_course_attend 里有对应听课记录。 */
    MATERIALIZED,

    /** 该单不再授权这节课。是否真的删掉听课记录取决于引用计数。 */
    REVOKED,

    /** 物化失败，等待重试或转人工。 */
    FAILED;

    public static GrantStatus parse(String value) {
        for (GrantStatus status : values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        return null;
    }
}
