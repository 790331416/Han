package com.han.common.core.util;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 集合工具类
 *
 * @deprecated 与 {@link HanCollUtil} 完全重复，已全部委托给 {@code HanCollUtil}。
 * 新代码请直接使用 {@link HanCollUtil}，存量调用点将在统一整改批次中迁移。
 */
@Deprecated(since = "1.0.0")
public final class XuCollUtil {

    private XuCollUtil() {}

    /**
     * 判断集合是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return HanCollUtil.isEmpty(collection);
    }

    /**
     * 判断集合是否非空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return HanCollUtil.isNotEmpty(collection);
    }

    /**
     * 判断数组是否为空
     */
    public static boolean isEmpty(Object[] array) {
        return HanCollUtil.isEmpty(array);
    }

    /**
     * 判断数组是否非空
     */
    public static boolean isNotEmpty(Object[] array) {
        return HanCollUtil.isNotEmpty(array);
    }

    /**
     * 获取第一个元素
     */
    public static <T> T getFirst(List<T> list) {
        return HanCollUtil.getFirst(list);
    }

    /**
     * 转换为列表
     */
    @SafeVarargs
    public static <T> List<T> toList(T... elements) {
        return HanCollUtil.toList(elements);
    }

    /**
     * 过滤
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        return HanCollUtil.filter(list, predicate);
    }

    /**
     * 映射
     */
    public static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
        return HanCollUtil.map(list, mapper);
    }

    /**
     * 分组
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        return HanCollUtil.groupBy(list, classifier);
    }

    /**
     * 去重
     */
    public static <T> List<T> distinct(List<T> list) {
        return HanCollUtil.distinct(list);
    }

    /**
     * 排序
     */
    public static <T> List<T> sort(List<T> list, Comparator<? super T> comparator) {
        return HanCollUtil.sort(list, comparator);
    }

    /**
     * 连接
     */
    public static String join(Collection<?> collection, String delimiter) {
        return HanCollUtil.join(collection, delimiter);
    }
}
