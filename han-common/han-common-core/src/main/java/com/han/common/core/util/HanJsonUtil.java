package com.han.common.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.han.common.core.json.HanJsonModuleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * JSON工具类
 * <p>
 * 本工具类的 {@code ObjectMapper} 与 Web 层的 Jackson 3 配置保持一致：
 * <ul>
 *   <li>日期时间统一输出 {@code yyyy-MM-dd HH:mm:ss}；反序列化同时兼容该格式与 ISO-8601，
 *       保证升级前写入 Redis 的历史数据仍可读回。</li>
 *   <li>通过 {@link HanJsonModuleProvider} SPI 装载上层模块贡献的序列化规则，
 *       {@code han-common-web} 借此把 {@code @Sensitive} 脱敏接进本路径 ——
 *       写缓存与打日志不再输出手机号 / 身份证明文。</li>
 * </ul>
 * 注意：与 Web 层不同，本工具类<b>不</b>把 {@code Long} 序列化为字符串。
 * Long→String 是为了规避前端精度丢失，缓存与日志侧启用它会让 {@link #parseMap(String)}
 * 之类的无类型读取拿到字符串，反而破坏既有调用契约。
 */
public final class HanJsonUtil {

    private static final Logger log = LoggerFactory.getLogger(HanJsonUtil.class);

    /** 与 han-common-web 的 JacksonAutoConfiguration 保持一致 */
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 反序列化侧同时接受 {@code yyyy-MM-dd HH:mm:ss} 与 ISO-8601 的 {@code T} 分隔形式 */
    private static final DateTimeFormatter LENIENT_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral(' ').optionalEnd()
            .optionalStart().appendLiteral('T').optionalEnd()
            .appendPattern("HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter LENIENT_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("HH:mm:ss")
            .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).optionalEnd()
            .toFormatter();

    private static final ObjectMapper OBJECT_MAPPER = buildObjectMapper();

    private HanJsonUtil() {}

    private static ObjectMapper buildObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        SimpleModule dateTimeModule = new SimpleModule("HanJson2DateTimeModule");
        dateTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));
        dateTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(LENIENT_DATE_TIME_FORMATTER));
        dateTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
        dateTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
        dateTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(TIME_FORMATTER));
        dateTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(LENIENT_TIME_FORMATTER));
        mapper.registerModule(dateTimeModule);

        registerSpiModules(mapper);
        return mapper;
    }

    private static void registerSpiModules(ObjectMapper mapper) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = HanJsonUtil.class.getClassLoader();
        }
        try {
            for (HanJsonModuleProvider provider : ServiceLoader.load(HanJsonModuleProvider.class, loader)) {
                com.fasterxml.jackson.databind.Module module = provider.getModule();
                if (module != null) {
                    mapper.registerModule(module);
                    log.debug("[HanJsonUtil] 已装载 JSON 模块: {}", module.getModuleName());
                }
            }
        } catch (RuntimeException e) {
            // 装载失败不能让工具类整体不可用，但必须可见 —— 静默降级会让脱敏重新变成绕过路径
            log.error("[HanJsonUtil] 装载 HanJsonModuleProvider 失败，脱敏等扩展规则可能未生效", e);
        }
    }

    /**
     * 对象转JSON字符串
     */
    public static String toJsonString(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("对象转JSON字符串失败", e);
        }
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T parseObject(String json, Class<T> clazz) {
        if (HanStrUtil.isBlank(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON字符串转对象失败", e);
        }
    }

    /**
     * JSON字符串转对象
     */
    public static <T> T parseObject(String json, TypeReference<T> typeReference) {
        if (HanStrUtil.isBlank(json)) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON字符串转对象失败", e);
        }
    }

    /**
     * JSON字符串转List
     */
    public static <T> List<T> parseList(String json, Class<T> clazz) {
        if (HanStrUtil.isBlank(json)) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON字符串转List失败", e);
        }
    }

    /**
     * JSON字符串转Map
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseMap(String json) {
        if (HanStrUtil.isBlank(json)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON字符串转Map失败", e);
        }
    }

    /**
     * 获取ObjectMapper实例
     * <p>返回的是内部实例的副本，调用方可以自由改配置而不会污染全局工具类。
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER.copy();
    }
}
