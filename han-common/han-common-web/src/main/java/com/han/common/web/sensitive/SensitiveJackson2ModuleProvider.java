package com.han.common.web.sensitive;

import com.fasterxml.jackson.databind.Module;
import com.han.common.core.json.HanJsonModuleProvider;

/**
 * 把 {@link SensitiveJackson2Module} 注入 {@code HanJsonUtil} 的 SPI 实现。
 * <p>在 {@code META-INF/services/com.han.common.core.json.HanJsonModuleProvider} 中声明。
 */
public class SensitiveJackson2ModuleProvider implements HanJsonModuleProvider {

    @Override
    public Module getModule() {
        return new SensitiveJackson2Module();
    }
}
