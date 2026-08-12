package com.han.system.sdfz.order;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.mapper.EduCourseOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 让后台任务与内部接口能在某个租户的上下文里跑。
 *
 * <p>租户过滤靠 {@code SecurityContextHolder} 里的登录用户，定时任务没有请求上下文，
 * 直接跑会被 {@code HanTenantLineHandler} 判成「取不到 tenantId」而<b>跳过全部租户条件</b>——
 * 那就是跨租户读写。所以这里显式地一个租户一个租户地跑。</p>
 */
@Component
@RequiredArgsConstructor
public class CourseOrderTenantScope {

    private final EduCourseOrderMapper orderMapper;

    /**
     * 有订购单的租户清单。只在这一步忽略租户过滤，拿到清单后立刻恢复隔离。
     */
    public List<Long> tenantsWithOrders() {
        List<Map<String, Object>> rows = TenantHelper.ignore(() ->
                orderMapper.selectMaps(new QueryWrapper<EduCourseOrderPo>()
                        .select("distinct tenant_id")));
        Set<Long> tenants = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get("tenant_id");
            if (value == null) {
                value = row.get("TENANT_ID");
            }
            if (value instanceof Number number) {
                tenants.add(number.longValue());
            }
        }
        return new ArrayList<>(tenants);
    }

    /**
     * 在指定租户上下文里执行，执行完把上下文还原，不污染调用线程。
     */
    public void runAs(Long tenantId, Consumer<Long> action) {
        LoginUser previous = SecurityContextHolder.getLoginUser();
        try {
            SecurityContextHolder.setLoginUser(LoginUser.builder().tenantId(tenantId).build());
            action.accept(tenantId);
        } finally {
            if (previous == null) {
                SecurityContextHolder.clear();
            } else {
                SecurityContextHolder.setLoginUser(previous);
            }
        }
    }
}
