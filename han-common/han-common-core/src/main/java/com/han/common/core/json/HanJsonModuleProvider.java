package com.han.common.core.json;

import com.fasterxml.jackson.databind.Module;

/**
 * {@link com.han.common.core.util.HanJsonUtil} 的模块扩展点（JDK {@link java.util.ServiceLoader} SPI）。
 * <p>
 * {@code han-common-core} 位于依赖链底层，无法反向依赖 {@code han-common-web} 里的脱敏注解。
 * 上层模块通过实现本接口并在 {@code META-INF/services/com.han.common.core.json.HanJsonModuleProvider}
 * 中声明，即可把自己的序列化规则注入到工具类使用的 {@code ObjectMapper}，从而让
 * 「写 Redis / 打日志」这条路径与 Web 层保持同一套规则。
 * <p>
 * 实现类必须有公开无参构造，且 {@link #getModule()} 不得返回 {@code null}。
 */
public interface HanJsonModuleProvider {

    /**
     * 待注册的 Jackson 2 模块。
     */
    Module getModule();
}
