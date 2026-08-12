package com.han.system.sdfz.compat;

/**
 * 旧三课堂 {@code status} 字段的语义转换。
 *
 * <p><b>这是一处反直觉的映射，改动前请先读完这段说明。</b>
 *
 * <p>旧系统 {@code CommonConstant.java:232} 的注释写的是「状态(0无效1有效)」，
 * <b>但注释是错的，代码全部反着用</b>：{@code TbEliteSchoolServiceImpl:142} 新增时置 {@code "0"}、
 * {@code :176} 删除时置 {@code "1"}，{@code TbCourseInfoServiceImpl:356/396} 新增置 {@code "0"}、
 * {@code :484/491} 删除置 {@code "1"}，且查询一律 {@code eq(status, "0")}。
 * 也就是说旧系统的 {@code status} 实际充当的是<b>软删除标志</b>。
 *
 * <p>因此正确的对应关系是：
 * <table border="1">
 *   <caption>状态字段对应关系</caption>
 *   <tr><th>旧系统 status</th><th>Han</th></tr>
 *   <tr><td>{@code "0"}</td><td>{@code del_flag = 0}（记录存在）</td></tr>
 *   <tr><td>{@code "1"}</td><td>{@code del_flag = 1}（已逻辑删除）</td></tr>
 * </table>
 *
 * <p>Han 的 {@code status} 是<b>另一个概念</b>——启用（0）/ 停用（1），与删除无关，
 * 绝不能直接写进旧系统的 {@code status}：那会让 Han 里停用的记录在旧侧被当成已删除，
 * 反过来把旧侧的删除标记读成停用，则会让已删除记录复活成正常记录并撞唯一索引。
 *
 * <p>依据：{@code doc/旧三课堂系统与数据库结构审计-2026-08-12.md} 第 3.4 节。
 * 该文档同时指出 {@code tb_live_info} 是例外（自带独立 {@code del_flag}，其 {@code status}
 * 表示直播生命周期），所以这条规则只适用于目录类实体，不要推广到直播。
 */
public final class LegacyStatus {

    /** 旧系统里表示「记录存在」的 status 取值。 */
    public static final String PRESENT = "0";

    /** 旧系统里表示「已删除」的 status 取值。 */
    public static final String DELETED = "1";

    private LegacyStatus() {
    }

    /** 把 Han 的 {@code del_flag} 翻译成旧系统的 {@code status}。 */
    public static String ofDelFlag(Integer delFlag) {
        return delFlag != null && delFlag != 0 ? DELETED : PRESENT;
    }

    /**
     * 旧侧传来的 status/state 过滤条件是否在筛「已删除」的记录。
     *
     * <p>Han 的逻辑删除由 MyBatis-Plus 的 {@code @TableLogic} 兜住，已删除的行根本查不出来，
     * 所以这类筛选只能返回空集合，而不是去比对 Han 的 {@code status} 列。
     */
    public static boolean selectsDeleted(String legacyStatus) {
        return legacyStatus != null && DELETED.equals(legacyStatus.trim());
    }
}
