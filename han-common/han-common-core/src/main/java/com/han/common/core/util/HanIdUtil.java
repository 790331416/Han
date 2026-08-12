package com.han.common.core.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ID生成工具类
 * <p>
 * 雪花 ID 的 {@code workerId} / {@code datacenterId} 按以下优先级解析，解析结果在类初始化时打印：
 * <ol>
 *   <li>系统属性 {@code han.id.worker-id} / {@code han.id.datacenter-id}</li>
 *   <li>环境变量 {@code HAN_WORKER_ID} / {@code HAN_DATACENTER_ID}</li>
 *   <li>本机 IPv4 末两段取模（同网段内多实例天然错开）</li>
 *   <li>主机名散列取模</li>
 * </ol>
 * 多实例部署时必须保证 {@code (datacenterId, workerId)} 组合唯一，容器编排下建议显式注入环境变量。
 */
public final class HanIdUtil {

    private static final Logger log = LoggerFactory.getLogger(HanIdUtil.class);

    /** workerId / datacenterId 各占 5 位，取值范围 0~31 */
    private static final long MAX_WORKER_ID = 31L;
    private static final long MAX_DATACENTER_ID = 31L;

    private static final String WORKER_ID_PROPERTY = "han.id.worker-id";
    private static final String DATACENTER_ID_PROPERTY = "han.id.datacenter-id";
    private static final String WORKER_ID_ENV = "HAN_WORKER_ID";
    private static final String DATACENTER_ID_ENV = "HAN_DATACENTER_ID";

    private static final long WORKER_ID;
    private static final long DATACENTER_ID;

    /** 默认雪花ID生成器（单例，线程安全） */
    private static final SnowflakeIdWorker DEFAULT_ID_WORKER;

    /** 按 (datacenterId, workerId) 复用 worker 实例，避免每次调用新建导致同毫秒重复 ID */
    private static final ConcurrentMap<Long, SnowflakeIdWorker> WORKER_CACHE = new ConcurrentHashMap<>();

    static {
        long[] resolved = resolveNodeIds();
        DATACENTER_ID = resolved[0];
        WORKER_ID = resolved[1];
        DEFAULT_ID_WORKER = new SnowflakeIdWorker(WORKER_ID, DATACENTER_ID);
        WORKER_CACHE.put(cacheKey(WORKER_ID, DATACENTER_ID), DEFAULT_ID_WORKER);
        log.info("[HanIdUtil] 雪花ID节点标识: datacenterId={}, workerId={}（多实例部署请确认该组合唯一）",
                DATACENTER_ID, WORKER_ID);
    }

    private HanIdUtil() {}

    /**
     * 生成UUID
     */
    public static String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 生成UUID（带分隔符）
     */
    public static String uuidWithHyphen() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成数字ID
     */
    public static long nextId() {
        return ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
    }

    /**
     * 生成数字ID（指定范围）
     */
    public static long nextId(long origin, long bound) {
        return ThreadLocalRandom.current().nextLong(origin, bound);
    }

    /**
     * 生成雪花ID（节点标识由启动时解析，见类注释）
     */
    public static long snowflakeId() {
        return DEFAULT_ID_WORKER.nextId();
    }

    /**
     * 生成雪花ID（指定 workerId 和 datacenterId）
     * <p>同一组 (workerId, datacenterId) 复用同一个生成器实例，可安全地反复调用。
     */
    public static long snowflakeId(long workerId, long datacenterId) {
        return WORKER_CACHE
                .computeIfAbsent(cacheKey(workerId, datacenterId), k -> new SnowflakeIdWorker(workerId, datacenterId))
                .nextId();
    }

    /**
     * 当前实例使用的 workerId
     */
    public static long getWorkerId() {
        return WORKER_ID;
    }

    /**
     * 当前实例使用的 datacenterId
     */
    public static long getDatacenterId() {
        return DATACENTER_ID;
    }

    private static long cacheKey(long workerId, long datacenterId) {
        return (datacenterId << 5) | workerId;
    }

    /**
     * @return {@code [datacenterId, workerId]}
     */
    private static long[] resolveNodeIds() {
        Long configuredWorker = readConfigured(WORKER_ID_PROPERTY, WORKER_ID_ENV, MAX_WORKER_ID);
        Long configuredDatacenter = readConfigured(DATACENTER_ID_PROPERTY, DATACENTER_ID_ENV, MAX_DATACENTER_ID);
        if (configuredWorker != null && configuredDatacenter != null) {
            return new long[]{configuredDatacenter, configuredWorker};
        }

        byte[] address = localAddressBytes();
        long derivedWorker;
        long derivedDatacenter;
        if (address != null && address.length >= 2) {
            derivedWorker = (address[address.length - 1] & 0xFF) % (MAX_WORKER_ID + 1);
            derivedDatacenter = (address[address.length - 2] & 0xFF) % (MAX_DATACENTER_ID + 1);
        } else {
            long hash = hostNameHash();
            derivedWorker = hash % (MAX_WORKER_ID + 1);
            derivedDatacenter = (hash / (MAX_WORKER_ID + 1)) % (MAX_DATACENTER_ID + 1);
        }

        long workerId = configuredWorker != null ? configuredWorker : derivedWorker;
        long datacenterId = configuredDatacenter != null ? configuredDatacenter : derivedDatacenter;
        if (configuredWorker == null || configuredDatacenter == null) {
            log.warn("[HanIdUtil] 未显式配置 {}/{}（或 {}/{}），已按本机网络标识推导 datacenterId={}, workerId={}。"
                            + "多实例部署时该推导不保证唯一，请显式注入。",
                    WORKER_ID_PROPERTY, DATACENTER_ID_PROPERTY, WORKER_ID_ENV, DATACENTER_ID_ENV,
                    datacenterId, workerId);
        }
        return new long[]{datacenterId, workerId};
    }

    private static Long readConfigured(String property, String env, long max) {
        String raw = System.getProperty(property);
        if (raw == null || raw.isBlank()) {
            raw = System.getenv(env);
        }
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long value = Long.parseLong(raw.trim());
            if (value < 0 || value > max) {
                log.warn("[HanIdUtil] {}/{} 取值 {} 超出 0~{} 范围，已忽略并回退到自动推导", property, env, value, max);
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            log.warn("[HanIdUtil] {}/{} 取值 {} 不是合法整数，已忽略并回退到自动推导", property, env, raw);
            return null;
        }
    }

    /**
     * 取一个非回环的本机 IPv4 地址字节；拿不到时返回 null。
     */
    private static byte[] localAddressBytes() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface nic = interfaces.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = nic.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    byte[] bytes = address.getAddress();
                    if (!address.isLoopbackAddress() && !address.isLinkLocalAddress() && bytes.length == 4) {
                        return bytes;
                    }
                }
            }
        } catch (SocketException e) {
            log.warn("[HanIdUtil] 读取本机网卡地址失败，回退到主机名散列", e);
        }
        return null;
    }

    private static long hostNameHash() {
        String hostName;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostName = System.getenv("HOSTNAME");
        }
        if (hostName == null || hostName.isBlank()) {
            hostName = UUID.randomUUID().toString();
        }
        return Math.abs((long) hostName.hashCode());
    }

    /**
     * 雪花ID生成器
     */
    private static class SnowflakeIdWorker {
        /** 允许容忍的时钟回拨毫秒数，超出则拒绝生成 */
        private static final long MAX_BACKWARD_TOLERANCE_MS = 5L;

        private final long twepoch = 1288834974657L;
        private final long workerIdBits = 5L;
        private final long datacenterIdBits = 5L;
        private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
        private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
        private final long sequenceBits = 12L;
        private final long workerIdShift = sequenceBits;
        private final long datacenterIdShift = sequenceBits + workerIdBits;
        private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;
        private final long sequenceMask = -1L ^ (-1L << sequenceBits);
        private long workerId;
        private long datacenterId;
        private long sequence = 0L;
        private long lastTimestamp = -1L;

        public SnowflakeIdWorker(long workerId, long datacenterId) {
            if (workerId > maxWorkerId || workerId < 0) {
                throw new IllegalArgumentException(String.format("worker Id can't be greater than %d or less than 0", maxWorkerId));
            }
            if (datacenterId > maxDatacenterId || datacenterId < 0) {
                throw new IllegalArgumentException(String.format("datacenter Id can't be greater than %d or less than 0", maxDatacenterId));
            }
            this.workerId = workerId;
            this.datacenterId = datacenterId;
        }

        public synchronized long nextId() {
            long timestamp = timeGen();
            if (timestamp < lastTimestamp) {
                long offset = lastTimestamp - timestamp;
                if (offset > MAX_BACKWARD_TOLERANCE_MS) {
                    throw new IllegalStateException(String.format("Clock moved backwards. Refusing to generate id for %d milliseconds", offset));
                }
                // NTP 校时的小幅抖动：自旋等到追平，不打断业务
                timestamp = tilNextMillis(lastTimestamp - 1);
            }
            if (lastTimestamp == timestamp) {
                sequence = (sequence + 1) & sequenceMask;
                if (sequence == 0) {
                    timestamp = tilNextMillis(lastTimestamp);
                }
            } else {
                sequence = 0L;
            }
            lastTimestamp = timestamp;
            return ((timestamp - twepoch) << timestampLeftShift) | (datacenterId << datacenterIdShift) | (workerId << workerIdShift) | sequence;
        }

        protected long timeGen() {
            return System.currentTimeMillis();
        }

        protected long tilNextMillis(long lastTimestamp) {
            long timestamp = timeGen();
            while (timestamp <= lastTimestamp) {
                timestamp = timeGen();
            }
            return timestamp;
        }
    }
}
