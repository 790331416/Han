package com.han.system.sdfz.compat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次兼容调用的业务结果。
 *
 * <p>2026-08-12 接口收敛后每条路径只剩一个消费者：目录全在通道 B，登录相关全在通道 C，
 * 所以业务数据本身不再需要按消费者分形态，差异只剩外层信封——
 * 通道 B 拿裸对象，通道 C 拿完整信封，由 {@link LegacyCompatSupport} 负责。
 *
 * <p>形态选择：
 * <ul>
 *   <li>{@link #same} —— 单个业务对象（B1/B2/B3/B5/B8/B15、登录与当前用户）；</li>
 *   <li>{@link #list} —— 数组。旧前端对这类结果直接当数组用
 *       （{@code state.classRoomList = res.result}、{@code res.result[].branchCode}），
 *       不是分页对象；</li>
 *   <li>{@link #page} —— {@code {records,total,...}} 分页对象（B6/B7/B12）。
 *       B12 的前端明确读 {@code result.records} 与 {@code result.total}。</li>
 * </ul>
 *
 * @param value  业务数据本身
 * @param uiCode 通道 C 信封里的 {@code code}，仅图形验证码要求 0，其余为 200
 */
public record LegacyPayload(Object value, int uiCode) {

    public static final int UI_OK = 200;
    /** 图形验证码接口的前端判定是 {@code res.code == 0 && res.success}，与其它接口不同。 */
    public static final int UI_CAPTCHA_OK = 0;

    public LegacyPayload {
        value = value != null ? value : Map.of();
    }

    public static LegacyPayload same(Object value) {
        return new LegacyPayload(value, UI_OK);
    }

    public static LegacyPayload list(List<?> items) {
        return new LegacyPayload(items != null ? items : List.of(), UI_OK);
    }

    public static LegacyPayload page(List<?> items, long total, int pageNo, int pageSize) {
        List<?> safe = items != null ? items : List.of();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("records", safe);
        value.put("total", total);
        value.put("pageNo", pageNo);
        value.put("pageSize", pageSize);
        value.put("pages", pageSize > 0 ? (total + pageSize - 1) / pageSize : 0);
        return new LegacyPayload(value, UI_OK);
    }

    public LegacyPayload withUiCode(int value) {
        return new LegacyPayload(this.value, value);
    }
}
