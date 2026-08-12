package com.han.common.doc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 接口文档配置，绑定 {@code han.doc.*}。
 * <p>
 * {@link #enabled} <b>默认 false</b>：接口文档会把全部接口的路径、参数结构、字段名和枚举值
 * 完整暴露出来，是攻击面侦察的最佳素材，生产环境不能默认开启。
 * 本地联调时在 {@code application-dev.yml} 里显式打开：
 * <pre>
 * han:
 *   doc:
 *     enabled: true
 * </pre>
 */
@Data
@ConfigurationProperties(prefix = "han.doc")
public class DocProperties {

    /**
     * 是否启用接口文档。默认关闭，只应在 dev / test 环境显式开启。
     */
    private boolean enabled = false;

    /**
     * 文档标题
     */
    private String title = "Han 平台接口文档";

    /**
     * 文档版本
     */
    private String version = "1.0.0";

    /**
     * 文档描述
     */
    private String description = "Han 企业级微服务平台 OpenAPI 3 接口文档";

    /**
     * 是否按 A / I / B 分层生成接口分组
     */
    private boolean groupByTier = true;
}
