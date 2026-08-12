package com.han.system.sdfz.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.system.sdfz.order.domain.EduCourseOrderGrantPo;
import com.han.system.sdfz.order.domain.EduCourseOrderPo;
import com.han.system.sdfz.order.domain.GrantStatus;
import com.han.system.sdfz.order.legacy.LegacyCourse;
import com.han.system.sdfz.order.mapper.EduCourseOrderGrantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 鎺堟潈鍙拌处鐨勬寔涔呭寲鍗曞厓銆?
 *
 * <p>鍒绘剰鎶婂彴璐﹀啓鎿嶄綔鍗曠嫭鎴愪竴涓?Bean锛氭壒閲忕墿鍖?b>涓嶈兘</b>鐢ㄤ竴涓ぇ浜嬪姟鍖呰捣鏉?
 * 锛圤RDER-09 瑕佹眰閮ㄥ垎澶辫触鍙噸璇曪紝涓€鏉″け璐ヤ笉鑳芥妸宸茬粡鎴愬姛鐨勫嚑鐧炬潯涓€璧峰洖婊氾級锛?
 * 鎵€浠ユ瘡鏉″彴璐︾敤 {@link Propagation#REQUIRES_NEW} 鐙珛鎻愪氦銆?
 * 濡傛灉杩欎簺鏂规硶鐣欏湪缂栨帓绫婚噷鑷皟鐢紝Spring 鐨勪唬鐞嗕笉浼氱敓鏁堬紝浜嬪姟浼犳挱鏄亣鐨勩€?/p>
 */
@Component
@RequiredArgsConstructor
public class CourseGrantLedger {

    private final EduCourseOrderGrantMapper grantMapper;

    /**
     * 骞傜瓑鍦板彇鍑猴紙涓嶅瓨鍦ㄥ垯寤猴級涓€琛屽彴璐︺€?
     *
     * <p>浠?{@code (order_id, course_id)} 涓洪敭锛屽敮涓€绾︽潫 {@code uq_edu_course_order_grant}
     * 鍏滃簳銆傞噸澶嶆墽琛屽彧浼氭洿鏂帮紝涓嶄細鏂板锛岃繖鏄?ORDER-07 涓?ORDER-08 鐨勮惤鐐广€?/p>
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public EduCourseOrderGrantPo upsertPending(EduCourseOrderPo order, LegacyCourse course, Long subjectId) {
        EduCourseOrderGrantPo existing = find(order.getId(), course.courseId());
        if (existing == null) {
            EduCourseOrderGrantPo created = new EduCourseOrderGrantPo();
            created.setTenantId(order.getTenantId());
            created.setOrderId(order.getId());
            created.setCourseId(course.courseId());
            created.setCourseName(course.courseName());
            created.setCourseBeginTime(course.timeBegin());
            created.setListenClassId(order.getListenClassId());
            created.setSubjectId(subjectId);
            created.setGrantStatus(GrantStatus.PENDING.name());
            created.setSuspendedFlag(0);
            created.setAttemptCount(0);
            grantMapper.insert(created);
            return created;
        }
        EduCourseOrderGrantPo update = new EduCourseOrderGrantPo();
        update.setId(existing.getId());
        update.setSubjectId(subjectId);
        update.setListenClassId(order.getListenClassId());
        update.setCourseName(course.courseName());
        update.setCourseBeginTime(course.timeBegin());
        // 宸叉挙閿€鐨勫彴璐﹂噸鏂版巿鏉冩椂瑕佸娲诲畠锛屽惁鍒欎細涓€鐩村仠鍦?REVOKED 涓婁笉鍐嶇墿鍖栥€?
        if (existing.status() == GrantStatus.REVOKED) {
            update.setGrantStatus(GrantStatus.PENDING.name());
            existing.setGrantStatus(GrantStatus.PENDING.name());
        }
        update.setSuspendedFlag(0);
        grantMapper.updateById(update);
        existing.setSubjectId(subjectId);
        existing.setSuspendedFlag(0);
        existing.setCourseName(course.courseName());
        existing.setCourseBeginTime(course.timeBegin());
        return existing;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markMaterialized(Long grantId, String attendId) {
        EduCourseOrderGrantPo update = new EduCourseOrderGrantPo();
        update.setId(grantId);
        update.setAttendId(attendId);
        update.setGrantStatus(GrantStatus.MATERIALIZED.name());
        update.setSuspendedFlag(0);
        update.setMaterializedTime(LocalDateTime.now());
        update.setLastAttemptTime(LocalDateTime.now());
        update.setLastError(null);
        grantMapper.updateById(update);
        // updateById 浼氳烦杩?null 瀛楁锛宭ast_error 瑕佹樉寮忔竻绌猴紝鍚﹀垯淇ソ涔嬪悗鏃х殑澶辫触鍘熷洜杩樻寕鍦ㄤ笂闈€?
        grantMapper.update(null, new LambdaUpdateWrapper<EduCourseOrderGrantPo>()
                .eq(EduCourseOrderGrantPo::getId, grantId)
                .set(EduCourseOrderGrantPo::getLastError, null));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(Long grantId, int previousAttempts, String error, boolean retryable) {
        EduCourseOrderGrantPo update = new EduCourseOrderGrantPo();
        update.setId(grantId);
        update.setGrantStatus(GrantStatus.FAILED.name());
        update.setLastError(truncate(error));
        update.setLastAttemptTime(LocalDateTime.now());
        // 涓嶅彲閲嶈瘯鐨勫け璐ヤ笉娑堣€楅噸璇曟鏁帮細瀹冧笉浼氬洜涓哄璇曞嚑娆″彉濂斤紝
        // 璁╄鏁板闀垮彧浼氭妸瀹冩帹杩囬槇鍊硷紝鎺╃洊鎺夌湡姝ｉ渶瑕佷汉宸ョ湅鐨勫師鍥犮€?
        update.setAttemptCount(retryable ? previousAttempts + 1 : previousAttempts);
        grantMapper.updateById(update);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRevoked(Long grantId) {
        EduCourseOrderGrantPo update = new EduCourseOrderGrantPo();
        update.setId(grantId);
        update.setGrantStatus(GrantStatus.REVOKED.name());
        update.setSuspendedFlag(0);
        update.setRevokedTime(LocalDateTime.now());
        grantMapper.updateById(update);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSuspended(Long grantId, boolean suspended) {
        EduCourseOrderGrantPo update = new EduCourseOrderGrantPo();
        update.setId(grantId);
        update.setSuspendedFlag(suspended ? 1 : 0);
        grantMapper.updateById(update);
    }

    public EduCourseOrderGrantPo find(Long orderId, String courseId) {
        return grantMapper.selectOne(new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                .eq(EduCourseOrderGrantPo::getOrderId, orderId)
                .eq(EduCourseOrderGrantPo::getCourseId, courseId)
                .last("limit 1"));
    }

    public List<EduCourseOrderGrantPo> findByOrder(Long orderId) {
        return grantMapper.selectList(new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                .eq(EduCourseOrderGrantPo::getOrderId, orderId));
    }

    public List<EduCourseOrderGrantPo> findByOrderAndStatus(Long orderId, GrantStatus status) {
        return grantMapper.selectList(new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                .eq(EduCourseOrderGrantPo::getOrderId, orderId)
                .eq(EduCourseOrderGrantPo::getGrantStatus, status.name()));
    }

    /**
     * 鏁颁竴鑺傝褰撳墠杩樻湁鍑犲紶鍗曞湪鎶婂畠鎺堟潈缁欒繖涓惉璁茬彮锛屽彲浠ユ帓闄ゆ煇涓€寮犲崟銆?
     *
     * <p>杩欐槸銆屾挙閿€鎸夊紩鐢ㄨ鏁帮紝涓嶆寜鍗曞垹闄ゃ€嶇殑鏍稿績銆傛病鏈夊畠锛屾挙閿€ A 鍗曚細鎶?B 鍗曟巿鏉冪殑鍚屼竴鑺傝
     * 涓€璧蜂粠 {@code tb_course_attend} 閲屾姽鎺夛紝閫犳垚瓒婃潈鎾ら攢銆?/p>
     *
     * <p>琚寕璧凤紙璁㈣喘鍗曞喕缁擄級鐨勫彴璐︿笉璁″叆锛氬畠瀵瑰簲鐨勫惉璇捐褰曞凡缁忔槸澶辨晥鐘舵€侊紝涓嶆瀯鎴愩€岃繕鏈変汉瑕佸惉銆嶃€?/p>
     */
    public long countActiveReferences(String courseId, Long listenClassId, Long excludeOrderId) {
        return grantMapper.selectCount(new LambdaQueryWrapper<EduCourseOrderGrantPo>()
                .eq(EduCourseOrderGrantPo::getCourseId, courseId)
                .eq(EduCourseOrderGrantPo::getListenClassId, listenClassId)
                .eq(EduCourseOrderGrantPo::getGrantStatus, GrantStatus.MATERIALIZED.name())
                .eq(EduCourseOrderGrantPo::getSuspendedFlag, 0)
                .ne(excludeOrderId != null, EduCourseOrderGrantPo::getOrderId, excludeOrderId));
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
