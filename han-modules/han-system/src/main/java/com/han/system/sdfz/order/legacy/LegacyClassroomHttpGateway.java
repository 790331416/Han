package com.han.system.sdfz.order.legacy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 走旧 api 内部接口的物化实现。
 *
 * <p><b>当前旧 api 侧尚未提供这组端点</b>，本类是按下面这份契约先把 Han 侧写好，等旧 api 补齐后
 * 把 {@code sdfz.order.legacy.channel} 从 {@code jdbc} 改成 {@code http} 即可切换，业务代码不动。
 * 之所以不能直接复用现有的 {@code /tb-course-info/updateCourseInfo}，原因见
 * {@link LegacyClassroomJdbcGateway} 的类注释。</p>
 *
 * <h2>需要旧 api 实现的契约</h2>
 *
 * 基址由 {@code sdfz.order.legacy.http.base-url} 给出，必须以 {@code /} 结尾；
 * 每个请求带 {@code X-Han-Inner-Token} 头做内部鉴权；响应统一是
 * {@code {"code":200,"result":...}}，{@code code != 200} 视为失败。
 *
 * <table>
 *   <caption>端点清单</caption>
 *   <tr><th>方法与路径</th><th>入参</th><th>result</th></tr>
 *   <tr><td>GET  {@code internal/course-attend/courses}</td>
 *       <td>{@code classId}、{@code from}、{@code to}（ISO-8601，如 {@code 2026-09-01T00:00:00}）</td>
 *       <td>课程数组</td></tr>
 *   <tr><td>GET  {@code internal/course-attend/course}</td><td>{@code courseId}</td><td>单个课程或 null</td></tr>
 *   <tr><td>POST {@code internal/course-attend/materialize}</td>
 *       <td>听课记录字段，见 {@link LegacyAttendRequest}</td>
 *       <td>{@code {"attendId":"..."}}，实现必须按 {@code (fkCourseId, classId)} 幂等</td></tr>
 *   <tr><td>POST {@code internal/course-attend/revoke}</td>
 *       <td>{@code {"courseId":"...","classId":"..."}}</td><td>无，记录不存在时也返回 200</td></tr>
 *   <tr><td>GET  {@code internal/course-attend/active}</td>
 *       <td>{@code courseId}、{@code classId}</td><td>布尔</td></tr>
 * </table>
 */
@Slf4j
public class LegacyClassroomHttpGateway implements LegacyClassroomGateway {

    /**
     * 这条通道是新契约，时间一律用 ISO-8601（{@code 2026-09-01T00:00:00}）。
     * 旧库里的 {@code yyyy-MM-dd HH:mm:ss} 带空格，放进查询串要转义，读起来和排错都更麻烦。
     */
    private static final DateTimeFormatter WIRE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** 对端若沿用旧库格式也能吃下。 */
    private static final DateTimeFormatter LEGACY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String TOKEN_HEADER = "X-Han-Inner-Token";

    private final RestClient restClient;
    private final String baseUrl;
    private final String token;

    public LegacyClassroomHttpGateway(RestClient restClient, String baseUrl, String token) {
        this.restClient = restClient;
        this.baseUrl = baseUrl == null || baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.token = token;
    }

    @Override
    public List<LegacyCourse> listCourses(String lectureClassId, LocalDateTime from, LocalDateTime to) {
        if (lectureClassId == null || lectureClassId.isBlank()) {
            return List.of();
        }
        // 用 LinkedHashMap 而不是 Map.of：查询串顺序固定，日志和抓包对起来省事。
        Map<String, String> query = new LinkedHashMap<>();
        query.put("classId", lectureClassId);
        query.put("from", format(from));
        query.put("to", format(to));
        JsonNode result = get("internal/course-attend/courses", query);
        List<LegacyCourse> courses = new ArrayList<>();
        if (result != null && result.isArray()) {
            for (JsonNode node : result) {
                courses.add(toCourse(node));
            }
        }
        return courses;
    }

    @Override
    public LegacyCourse findCourse(String courseId) {
        if (courseId == null || courseId.isBlank()) {
            return null;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("courseId", courseId);
        JsonNode result = get("internal/course-attend/course", query);
        return result == null || result.isNull() ? null : toCourse(result);
    }

    @Override
    public String materializeAttend(LegacyAttendRequest request) {
        JsonNode result = post("internal/course-attend/materialize", request);
        String attendId = result == null ? null : text(result, "attendId");
        if (attendId == null || attendId.isBlank()) {
            throw LegacyClassroomException.permanent("三课堂物化接口未返回 attendId");
        }
        return attendId;
    }

    @Override
    public void revokeAttend(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return;
        }
        post("internal/course-attend/revoke", Map.of("courseId", courseId, "classId", classId));
    }

    @Override
    public boolean isAttendActive(String courseId, String classId) {
        if (courseId == null || classId == null) {
            return false;
        }
        Map<String, String> query = new LinkedHashMap<>();
        query.put("courseId", courseId);
        query.put("classId", classId);
        JsonNode result = get("internal/course-attend/active", query);
        return result != null && result.asBoolean(false);
    }

    /**
     * 用 {@link UriComponentsBuilder} 拼 URI 并传 {@link URI} 而不是字符串：
     * {@code RestClient.uri(String)} 会把字符串当 URI 模板再编码一次，自己先编码就会变成双重编码
     * （{@code :} 变 {@code %253A}），对端拿到的是带百分号的字面值。
     */
    private JsonNode get(String path, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        for (Map.Entry<String, String> entry : query.entrySet()) {
            if (entry.getValue() != null) {
                builder.queryParam(entry.getKey(), entry.getValue());
            }
        }
        URI uri = builder.build().encode(StandardCharsets.UTF_8).toUri();
        return unwrap(exchange(() -> restClient.get()
                .uri(uri)
                .header(TOKEN_HEADER, token)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(JsonNode.class)));
    }

    private JsonNode post(String path, Object body) {
        return unwrap(exchange(() -> restClient.post()
                .uri(baseUrl + path)
                .header(TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class)));
    }

    private static JsonNode exchange(java.util.function.Supplier<JsonNode> action) {
        try {
            return action.get();
        } catch (RestClientException ex) {
            // 网络层失败一律按可重试处理：旧 api 重启、网关抖动都属于这一类。
            throw LegacyClassroomException.retryable(
                    "三课堂内部接口不可达: " + ex.getClass().getSimpleName(), ex);
        }
    }

    private static JsonNode unwrap(JsonNode response) {
        if (response == null) {
            throw LegacyClassroomException.retryable("三课堂内部接口返回空响应", null);
        }
        JsonNode code = response.get("code");
        if (code == null || code.asInt(-1) != 200) {
            String message = text(response, "message");
            throw LegacyClassroomException.permanent(
                    "三课堂内部接口返回失败: code=" + (code == null ? "null" : code.asInt(-1))
                            + (message == null ? "" : ", message=" + message));
        }
        return response.get("result");
    }

    private static LegacyCourse toCourse(JsonNode node) {
        return new LegacyCourse(
                text(node, "courseId"),
                text(node, "courseName"),
                text(node, "courseType"),
                text(node, "classId"),
                text(node, "className"),
                text(node, "organId"),
                text(node, "organName"),
                text(node, "subjectCode"),
                text(node, "subjectName"),
                parse(text(node, "timeBegin")),
                parse(text(node, "timeEnd")));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    private static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        for (DateTimeFormatter formatter : List.of(WIRE_TIME, LEGACY_TIME)) {
            try {
                return LocalDateTime.parse(value, formatter);
            } catch (DateTimeParseException ignored) {
                // 换下一种格式再试
            }
        }
        log.warn("三课堂内部接口返回的时间无法解析，按缺失处理");
        return null;
    }

    private static String format(LocalDateTime value) {
        return value == null ? null : value.format(WIRE_TIME);
    }
}
