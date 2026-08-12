package com.han.system.sdfz.order.domain;

/**
 * 订购粒度。
 *
 * <p>两种粒度<b>唯一</b>的差异是候选课程集怎么算（见 {@code CourseGrantService}）。
 * 状态机、唯一约束、物化流程、撤销规则、重试机制全部共用，不分叉。</p>
 */
public enum GrantScope {

    /** 整班打包：订购主讲班本学期全部课程，主讲班新开任何科目都自动纳入，无需改单。 */
    WHOLE_CLASS,

    /** 按科目：只订购明细表里列出的科目，未列出的科目即使新开也不授权。 */
    BY_SUBJECT;

    public static GrantScope parse(String value) {
        for (GrantScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value)) {
                return scope;
            }
        }
        return null;
    }
}
