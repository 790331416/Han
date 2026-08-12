package com.han.common.log.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * 模块名称
     */
    String module() default "";

    /**
     * 操作类型
     */
    OperType type() default OperType.OTHER;

    /**
     * 是否记录请求参数
     */
    boolean saveParams() default true;

    /**
     * 是否记录响应结果
     */
    boolean saveResult() default true;

    /**
     * 操作类型枚举
     * <p>
     * {@code code} 是落库值，取值与历史 {@code ordinal()} 完全一致，因此存量数据含义不变。
     * 定义显式 code 是为了摆脱对枚举声明顺序的依赖 —— 往中间插一个值就会让所有历史数据漂移。
     * <p>
     * <b>注意</b>：han-system 的导出映射与前端展示映射目前与本枚举对不上（也彼此对不上），
     * 审计页面显示的操作类型是错的。那两处不在本模块内，需要 system 组与前端组一并订正。
     */
    enum OperType {
        /** 其他 */
        OTHER(0),
        /** 新增 */
        INSERT(1),
        /** 修改 */
        UPDATE(2),
        /** 删除 */
        DELETE(3),
        /** 查询 */
        SELECT(4),
        /** 列表查询 */
        QUERY(5),
        /** 导出 */
        EXPORT(6),
        /** 导入 */
        IMPORT(7),
        /** 授权 */
        GRANT(8),
        /** 强退 */
        FORCE_LOGOUT(9),
        /** 清空数据 */
        CLEAN(10);

        private final int code;

        OperType(int code) {
            this.code = code;
        }

        /**
         * 落库值
         */
        public int getCode() {
            return code;
        }

        /**
         * 按落库值反查枚举，未知值归为 {@link #OTHER}。
         */
        public static OperType fromCode(Integer code) {
            if (code != null) {
                for (OperType type : values()) {
                    if (type.code == code) {
                        return type;
                    }
                }
            }
            return OTHER;
        }
    }
}
