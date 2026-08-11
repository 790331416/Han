package com.han.auth.sdfz.digitalcampus;

import com.han.common.core.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 数字校园当前用户适配器。
 *
 * <p>本类只负责校验上游 Token、解密响应并转换已确认字段；不持久化 Token，也不负责创建 Han 用户。
 */
@Slf4j
@Service
public class DigitalCampusClient {

    static final String CURRENT_USER_PATH = "user/user/getOneById";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final boolean enabled;
    private final String currentUserUrl;
    private final Set<String> allowedRoleTypes;

    @Autowired
    public DigitalCampusClient(
            ObjectMapper objectMapper,
            @Value("${sdfz.digital-campus.enabled:false}") boolean enabled,
            @Value("${sdfz.digital-campus.api-base-url:}") String apiBaseUrl,
            @Value("${sdfz.digital-campus.allowed-role-types:2,5}") String allowedRoleTypes) {
        this(objectMapper, enabled, apiBaseUrl, allowedRoleTypes, productionRestClientBuilder());
    }

    DigitalCampusClient(ObjectMapper objectMapper, boolean enabled, String apiBaseUrl,
                        String allowedRoleTypes, RestClient.Builder restClientBuilder) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.currentUserUrl = joinUrl(apiBaseUrl, CURRENT_USER_PATH);
        this.allowedRoleTypes = parseAllowedRoleTypes(allowedRoleTypes);
        this.restClient = restClientBuilder.build();
    }

    public DigitalCampusProfile fetchCurrentUser(String token) {
        requireConfigured();
        String body;
        try {
            body = restClient.post()
                    .uri(currentUserUrl)
                    .header("access-token", token)
                    .header("x-platform", DigitalCampusAesCodec.encrypt(CURRENT_USER_PATH, token))
                    .header("x-time-stamp", DigitalCampusAesCodec.encryptedTimestamp(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException e) {
            log.warn("数字校园当前用户接口调用失败: {}", e.getClass().getSimpleName());
            throw new BusinessException("数字校园用户中心暂不可用");
        }
        return parseProfile(decodeEnvelope(body, token));
    }

    private JsonNode decodeEnvelope(String body, String token) {
        if (body == null || body.isBlank()) {
            throw new BusinessException("数字校园未返回用户信息");
        }
        try {
            JsonNode envelope = objectMapper.readTree(body);
            if (envelope.path("code").asInt(-1) == 2000 && envelope.path("result").isTextual()) {
                envelope = objectMapper.readTree(DigitalCampusAesCodec.decrypt(envelope.path("result").asText(), token));
            }
            int code = envelope.path("code").asInt(-1);
            if (code != 200 || !envelope.path("success").asBoolean(false)) {
                throw new BusinessException("数字校园 Token 无效或已过期");
            }
            JsonNode result = envelope.path("result");
            if (result.isTextual()) {
                result = objectMapper.readTree(result.asText());
            }
            if (!result.isObject() || result.isEmpty()) {
                throw new BusinessException("数字校园未返回用户信息");
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("数字校园用户信息格式无效");
        }
    }

    private DigitalCampusProfile parseProfile(JsonNode result) {
        JsonNode roles = result.path("roles");
        if (!roles.isArray()) {
            throw new BusinessException("数字校园未返回身份信息");
        }
        List<DigitalCampusProfile.Identity> identities = new java.util.ArrayList<>();
        for (JsonNode role : roles) {
            String roleType = text(role, "roleType");
            String identityId = text(role, "identityId");
            if (!allowedRoleTypes.contains(roleType) || identityId.isBlank()) {
                continue;
            }
            identities.add(new DigitalCampusProfile.Identity(
                    text(role, "userId"), text(role, "userName"), identityId,
                    text(role, "identityName"), roleType,
                    text(role, "schoolId"), text(role, "schoolName"),
                    text(role, "branchId"), text(role, "branchName"),
                    text(role, "isSchool"), text(role, "areaCode"),
                    parseDuties(role.path("dutyType")), parseClasses(role.path("classes"))));
        }
        if (identities.isEmpty()) {
            throw new BusinessException("数字校园账号没有可用的三课堂身份");
        }
        return new DigitalCampusProfile(text(result, "wxPhoneNumber"), identities);
    }

    private List<DigitalCampusProfile.Duty> parseDuties(JsonNode duties) {
        if (!duties.isArray()) {
            return List.of();
        }
        List<DigitalCampusProfile.Duty> result = new java.util.ArrayList<>();
        duties.forEach(duty -> result.add(new DigitalCampusProfile.Duty(
                text(duty, "pkId"), text(duty, "roleType"),
                text(duty, "positionName"), text(duty, "itemText"))));
        return result;
    }

    private List<DigitalCampusProfile.ClassMembership> parseClasses(JsonNode classes) {
        if (!classes.isArray()) {
            return List.of();
        }
        List<DigitalCampusProfile.ClassMembership> result = new java.util.ArrayList<>();
        classes.forEach(item -> result.add(new DigitalCampusProfile.ClassMembership(
                text(item, "branchId"), text(item, "branchName"),
                text(item, "classRoleId"), text(item, "name"),
                text(item, "schoolId"), text(item, "schoolName"),
                text(item, "schoolLevel"), text(item, "areaCode"),
                text(item, "eduDepartId"), text(item, "eduDepartName"),
                text(item, "cityEduDepartId"), text(item, "cityEduDepartName"),
                text(item, "countyEduDepartId"), text(item, "countyEduDepartName"),
                text(item, "townEduDepartId"), text(item, "townEduDepartName"))));
        return result;
    }

    private void requireConfigured() {
        if (!enabled || currentUserUrl.isBlank()) {
            throw new BusinessException("数字校园登录未配置");
        }
    }

    private static String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isValueNode() ? value.asText("") : "";
    }

    private static String joinUrl(String baseUrl, String path) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.replaceAll("/+$", "") + "/" + path;
    }

    private static Set<String> parseAllowedRoleTypes(String value) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value != null ? value.split(",") : new String[0])
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .forEach(result::add);
        return Set.copyOf(result);
    }

    private static RestClient.Builder productionRestClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(6));
        return RestClient.builder().requestFactory(factory);
    }
}
