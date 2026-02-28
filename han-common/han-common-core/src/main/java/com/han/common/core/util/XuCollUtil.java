package com.han.common.core.util;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

/**
 * 集合工具类
 */
public final class XuCollUtil {

    private XuCollUtil() {}

    /**
     * 判断集合是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * 判断集合是否非空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * 判断数组是否为空
     */
    public static boolean isEmpty(Object[] array) {
        return array == null || array.length == 0;
    }

    /**
     * 判断数组是否非空
     */
    public static boolean isNotEmpty(Object[] array) {
        return !isEmpty(array);
    }

    /**
     * 获取第一个元素
     */
    public static <T> T getFirst(List<T> list) {
        return isEmpty(list) ? null : list.get(0);
    }

    /**
     * 转换为列表
     */
    public static <T> List<T> toList(T... elements) {
        if (elements == null || elements.length == 0) {
            return new ArrayList<>();
        }
        return Arrays.asList(elements);
    }

    /**
     * 过滤
     */
    public static <T> List<T> filter(List<T> list, Predicate<T> predicate) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().filter(predicate).collect(Collectors.toList());
    }

    /**
     * 映射
     */
    public static <T, R> List<R> map(List<T> list, Function<T, R> mapper) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 分组
     */
    public static <T, K> Map<K, List<T>> groupBy(List<T> list, Function<T, K> classifier) {
        if (isEmpty(list)) {
            return new HashMap<>();
        }
        return list.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * 去重
     */
    public static <T> List<T> distinct(List<T> list) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 排序
     */
    public static <T> List<T> sort(List<T> list, Comparator<? super T> comparator) {
        if (isEmpty(list)) {
            return new ArrayList<>();
        }
        return list.stream().sorted(comparator).collect(Collectors.toList());
    }

    /**
     * 连接
     */
    public static String join(Collection<?> collection, String delimiter) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }
}
