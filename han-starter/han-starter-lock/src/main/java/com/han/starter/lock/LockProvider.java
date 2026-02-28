package com.han.starter.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁提供者接口（可插拔）
 */
public interface LockProvider {

    /**
     * 获取锁
     *
     * @param key 锁的key
     * @return 是否获取成功
     */
    boolean lock(String key);

    /**
     * 获取锁（带超时）
     *
     * @param key      锁的key
     * @param timeout  等待超时时间
     * @param leaseTime 锁持有时间
     * @return 是否获取成功
     */
    boolean lock(String key, Duration timeout, Duration leaseTime);

    /**
     * 尝试获取锁
     *
     * @param key 锁的key
     * @return 是否获取成功
     */
    boolean tryLock(String key);

    /**
     * 尝试获取锁（带超时）
     *
     * @param key      锁的key
     * @param timeout  等待超时时间
     * @param leaseTime 锁持有时间
     * @return 是否获取成功
     */
    boolean tryLock(String key, Duration timeout, Duration leaseTime);

    /**
     * 释放锁
     *
     * @param key 锁的key
     */
    void unlock(String key);

    /**
     * 执行带锁的操作
     *
     * @param key      锁的key
     * @param supplier 业务逻辑
     * @return 业务返回值
     */
    <T> T executeWithLock(String key, Supplier<T> supplier);

    /**
     * 执行带锁的操作（带超时）
     *
     * @param key       锁的key
     * @param timeout   等待超时时间
     * @param leaseTime 锁持有时间
     * @param supplier  业务逻辑
     * @return 业务返回值
     */
    <T> T executeWithLock(String key, Duration timeout, Duration leaseTime, Supplier<T> supplier);
}
