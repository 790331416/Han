package com.xuman.common.core.util;

import cn.hutool.core.collection.CollUtil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 集合工具类（封装Hutool）
 */
public final class XuCollUtil {

    private XuCollUtil() {}

    /**
     * 判断集合是否为空
     */
    public static boolean isEmpty(Collection<?> collection) {
        return CollUtil.isEmpty(collection);
    }

    /**
     * 判断集合是否非空
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return CollUtil.isNotEmpty(collection);
    }

    /**
     * 判断Map是否为空
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return CollUtil.isEmpty(map);
    }

    /**
     * 判断Map是否非空
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return CollUtil.isNotEmpty(map);
    }

    /**
     * 获取集合第一个元素
     */
    public static <T> T getFirst(Collection<T> collection) {
        return CollUtil.getFirst(collection);
    }

    /**
     * 获取集合最后一个元素
     */
    public static <T> T getLast(Collection<T> collection) {
        return CollUtil.getLast(collection);
    }

    /**
     * 创建新的ArrayList
     */
    @SafeVarargs
    public static <T> List<T> newArrayList(T... values) {
        return CollUtil.newArrayList(values);
    }

    /**
     * 创建新的HashSet
     */
    @SafeVarargs
    public static <T> Set<T> newHashSet(T... values) {
        return CollUtil.newHashSet(values);
    }

    /**
     * 提取集合中某个字段组成新集合
     */
    public static <T, R> List<R> map(Collection<T> collection, Function<T, R> mapper) {
        if (isEmpty(collection)) {
            return List.of();
        }
        return collection.stream().map(mapper).collect(Collectors.toList());
    }

    /**
     * 提取集合中某个字段组成Set
     */
    public static <T, R> Set<R> mapToSet(Collection<T> collection, Function<T, R> mapper) {
        if (isEmpty(collection)) {
            return Set.of();
        }
        return collection.stream().map(mapper).collect(Collectors.toSet());
    }

    /**
     * 集合转Map
     */
    public static <T, K> Map<K, T> toMap(Collection<T> collection, Function<T, K> keyMapper) {
        if (isEmpty(collection)) {
            return Map.of();
        }
        return collection.stream().collect(Collectors.toMap(keyMapper, Function.identity(), (a, b) -> a));
    }

    /**
     * 集合转Map
     */
    public static <T, K, V> Map<K, V> toMap(Collection<T> collection, 
                                            Function<T, K> keyMapper, 
                                            Function<T, V> valueMapper) {
        if (isEmpty(collection)) {
            return Map.of();
        }
        return collection.stream().collect(Collectors.toMap(keyMapper, valueMapper, (a, b) -> a));
    }

    /**
     * 集合分组
     */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection, Function<T, K> classifier) {
        if (isEmpty(collection)) {
            return Map.of();
        }
        return collection.stream().collect(Collectors.groupingBy(classifier));
    }

    /**
     * 集合去重
     */
    public static <T> List<T> distinct(Collection<T> collection) {
        if (isEmpty(collection)) {
            return List.of();
        }
        return collection.stream().distinct().collect(Collectors.toList());
    }

    /**
     * 合并集合
     */
    public static <T> List<T> union(Collection<T> coll1, Collection<T> coll2) {
        return CollUtil.union(coll1, coll2).stream().toList();
    }

    /**
     * 交集
     */
    public static <T> Collection<T> intersection(Collection<T> coll1, Collection<T> coll2) {
        return CollUtil.intersection(coll1, coll2);
    }

    /**
     * 差集（coll1 - coll2）
     */
    public static <T> Collection<T> subtract(Collection<T> coll1, Collection<T> coll2) {
        return CollUtil.subtract(coll1, coll2);
    }

    /**
     * 分割集合
     */
    public static <T> List<List<T>> split(Collection<T> collection, int size) {
        return CollUtil.split(collection, size);
    }
}
