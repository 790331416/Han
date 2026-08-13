package com.han.gateway.config;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * docker profile 的路由表必须覆盖基线 application.yml 的全部路由。
 *
 * <p>Spring 加载 profile 配置时，同名的 list 属性是<b>整表替换</b>而不是逐项合并。
 * 因此在 {@code application.yml} 里新增一条路由、忘了同步到 {@code application-docker.yml}，
 * 本地起服务（无 profile）一切正常，容器部署（{@code SPRING_PROFILES_ACTIVE=docker}）却 404。
 *
 * <p>2026-08-13 就是这么漏掉了 {@code sdfz-legacy-compat}：三课堂兼容层服务侧完全正常，
 * 但网关在 docker profile 下没有这条路由，整条 {@code /sdfz-compat/**} 前缀返回 404，
 * 现场排查时极易误判成兼容层本身没生效。过滤器级别的单测发现不了，因为它们不读 profile 配置。
 */
class GatewayRouteProfileParityTest {

    @Test
    void dockerProfileKeepsEveryRouteDeclaredInTheBaseline() {
        List<String> baseline = routeIds("/application.yml");
        List<String> docker = routeIds("/application-docker.yml");

        assertThat(baseline).isNotEmpty();
        assertThat(docker).isNotEmpty();
        assertThat(docker)
                .as("application-docker.yml 缺少基线里已声明的路由；profile 的 routes 是整表覆盖，必须同步补齐")
                .containsAll(baseline);
    }

    @SuppressWarnings("unchecked")
    private static List<String> routeIds(String resource) {
        try (InputStream in = GatewayRouteProfileParityTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("找不到配置文件 %s", resource).isNotNull();
            Map<String, Object> root = new Yaml().load(in);
            Object routes = dig(root, "spring", "cloud", "gateway", "server", "webflux", "routes");
            List<String> ids = new ArrayList<>();
            for (Object route : (List<Object>) routes) {
                ids.add(String.valueOf(((Map<String, Object>) route).get("id")));
            }
            return ids;
        } catch (Exception e) {
            throw new IllegalStateException("解析 " + resource + " 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> root, String... path) {
        Object node = root;
        for (String key : path) {
            node = ((Map<String, Object>) node).get(key);
            if (node == null) {
                throw new IllegalStateException("配置里找不到路径节点: " + key);
            }
        }
        return node;
    }
}
