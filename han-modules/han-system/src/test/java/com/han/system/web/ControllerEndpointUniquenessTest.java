package com.han.system.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 断言 han-system 的 HTTP 端点在启动期不会撞车。
 *
 * <p>为什么需要这条测试：两个 Controller 各自 {@code @RequestMapping("/system/education")}、
 * 又各写一遍 {@code /semesters/*} 与 {@code /rooms/*} 时，编译与 {@code mvn install} 全都能过，
 * 只有真正启动 Spring 注册 handler method 时才会抛
 * {@code IllegalStateException: Ambiguous mapping}，服务直接起不来。
 * 2026-08-12 合并教育主数据返工线与课程订购线时就正面撞上了这一种，靠人工看是看不住的。</p>
 *
 * <p>这里不起完整上下文（那要连 MySQL、Redis 与 Nacos），而是走 Spring MVC 真正用来建注册表的那条路：
 * 扫出全部 Controller，只登记 <b>bean 定义</b>（不实例化，因此不需要任何外部依赖），
 * 交给 {@link RequestMappingHandlerMapping#afterPropertiesSet()} 自己去建映射表。
 * 冲突判定逻辑与运行期完全一致——就是同一个类里的同一段代码。</p>
 */
@DisplayName("控制器端点唯一性")
class ControllerEndpointUniquenessTest {

    private static final String SCAN_BASE_PACKAGE = "com.han";

    @Test
    @DisplayName("全部控制器端点两两不重复，Spring 启动期不会抛 Ambiguous mapping")
    void controllerEndpointsAreUnique() {
        Map<RequestMappingInfo, HandlerMethod> mappings = buildMappings();

        assertThat(mappings).as("应当扫描到控制器端点，扫不到说明测试本身失效了").isNotEmpty();
    }

    @Test
    @DisplayName("学期与教室端点只由 EducationCalendarController 提供")
    void semesterAndRoomEndpointsHaveExactlyOneOwner() {
        Map<RequestMappingInfo, HandlerMethod> mappings = buildMappings();

        Map<String, List<String>> owners = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : mappings.entrySet()) {
            for (String pattern : patternsOf(entry.getKey())) {
                if (!pattern.startsWith("/system/education/semesters")
                        && !pattern.startsWith("/system/education/rooms")) {
                    continue;
                }
                for (String method : methodsOf(entry.getKey())) {
                    owners.computeIfAbsent(method + " " + pattern, key -> new ArrayList<>())
                            .add(entry.getValue().getBeanType().getSimpleName());
                }
            }
        }

        assertThat(owners).as("学期与教室端点应当存在").isNotEmpty();
        assertThat(owners).allSatisfy((endpoint, handlers) ->
                assertThat(handlers)
                        .as("端点 %s 只能有一个归属控制器", endpoint)
                        .containsOnly("EducationCalendarController"));
    }

    /**
     * 全部控制器都登记成 <b>lazy-init</b> 的 bean 定义：refresh 时不会实例化它们，
     * 因此构造参数、数据源、Redis、Nacos 一个都不会被碰；而
     * {@code RequestMappingHandlerMapping} 建表只需要 {@code getType(beanName)} 拿类型，
     * 拿得到。一旦出现重复端点，{@code afterPropertiesSet()} 就在这里抛
     * {@code IllegalStateException: Ambiguous mapping}——与服务真正启动时是同一段代码。
     */
    private static Map<RequestMappingInfo, HandlerMethod> buildMappings() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            ClassPathScanningCandidateComponentProvider scanner =
                    new ClassPathScanningCandidateComponentProvider(false);
            // @RestController 元注解了 @Controller，AnnotationTypeFilter 默认认元注解
            scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

            scanner.findCandidateComponents(SCAN_BASE_PACKAGE).forEach(candidate -> {
                RootBeanDefinition definition = new RootBeanDefinition(candidate.getBeanClassName());
                definition.setLazyInit(true);
                context.registerBeanDefinition(candidate.getBeanClassName(), definition);
            });
            context.refresh();

            RequestMappingHandlerMapping mapping = new RequestMappingHandlerMapping();
            mapping.setApplicationContext(context);
            mapping.afterPropertiesSet();
            return mapping.getHandlerMethods();
        }
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() != null) {
            Set<String> patterns = new TreeSet<>();
            info.getPathPatternsCondition().getPatterns().forEach(pattern -> patterns.add(pattern.getPatternString()));
            return patterns;
        }
        PatternsRequestCondition condition = info.getPatternsCondition();
        return condition == null ? Set.of() : new TreeSet<>(condition.getPatterns());
    }

    private static Set<String> methodsOf(RequestMappingInfo info) {
        RequestMethodsRequestCondition condition = info.getMethodsCondition();
        if (condition == null || condition.getMethods().isEmpty()) {
            return Set.of("ANY");
        }
        Set<String> methods = new TreeSet<>();
        condition.getMethods().forEach(method -> methods.add(method.name()));
        return methods;
    }
}
