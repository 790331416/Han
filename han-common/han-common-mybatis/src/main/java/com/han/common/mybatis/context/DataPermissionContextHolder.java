package com.han.common.mybatis.context;

import com.han.common.mybatis.annotation.DataPermission;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 数据权限注解上下文。
 *
 * <p>{@code DataPermissionInterceptor} 只能看到 Mapper 语句 ID，拿不到 Service 方法上的注解。
 * 本类由 {@code DataPermissionAspect} 在进入被标注的 Service 方法时压栈，
 * 让处理器可以感知到 Service 层声明的数据范围。</p>
 *
 * <p>用栈结构是为了支持嵌套调用：内层方法退出后仍能恢复外层的声明。</p>
 */
public final class DataPermissionContextHolder {

    private static final ThreadLocal<Deque<DataPermission>> HOLDER = ThreadLocal.withInitial(ArrayDeque::new);

    private DataPermissionContextHolder() {
    }

    public static void push(DataPermission dataPermission) {
        if (dataPermission == null) {
            return;
        }
        HOLDER.get().push(dataPermission);
    }

    /**
     * 弹出栈顶声明；栈空时清理 ThreadLocal，避免线程池复用时残留。
     */
    public static void poll() {
        Deque<DataPermission> deque = HOLDER.get();
        deque.poll();
        if (deque.isEmpty()) {
            HOLDER.remove();
        }
    }

    /**
     * 当前生效的数据权限声明，没有则返回 null。
     */
    public static DataPermission get() {
        return HOLDER.get().peek();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
