package com.han.system.sdfz.order.legacy;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 三课堂物化通道配置。
 *
 * <p>凭据一律从环境变量注入，不写进仓库里的任何 yml。</p>
 */
@Data
@ConfigurationProperties(prefix = "sdfz.order.legacy")
public class LegacyClassroomProperties {

    /**
     * 物化通道。
     *
     * <ul>
     *   <li>{@code disabled}（默认）：不物化。订购单在 Han 侧照常闭环，同步动作直接报错，
     *       已经物化过的听课记录不受影响。</li>
     *   <li>{@code jdbc}：直连三课堂库。当前唯一可用的通道，原因见 {@link LegacyClassroomJdbcGateway}。</li>
     *   <li>{@code http}：走旧 api 内部接口。等旧 api 侧补齐契约后切换，只改这一项。</li>
     * </ul>
     */
    private Channel channel = Channel.DISABLED;

    private Jdbc jdbc = new Jdbc();

    private Http http = new Http();

    /** 写进 tb_course_attend 的 create_id / update_id，便于在旧库里区分 Han 物化与教师手工建课。 */
    private String operatorId = "han-order";

    private String operatorName = "Han订购授权";

    public enum Channel {
        DISABLED,
        JDBC,
        HTTP
    }

    @Data
    public static class Jdbc {
        private String url;
        private String username;
        private String password;
        private int maximumPoolSize = 4;
        private Duration connectionTimeout = Duration.ofSeconds(5);
    }

    @Data
    public static class Http {
        /** 旧 api 内部接口基址，必须以 / 结尾。 */
        private String baseUrl;
        private Duration connectTimeout = Duration.ofSeconds(3);
        /** 旧 api CommonService 的超时是 6 秒，这里保持同量级。 */
        private Duration readTimeout = Duration.ofSeconds(6);
        /** 内部调用令牌，从环境变量注入。 */
        private String token;
    }
}
