package com.han.system.sdfz.order.support;

import com.han.system.sdfz.order.legacy.LegacyAttendRequest;
import com.han.system.sdfz.order.legacy.LegacyClassroomException;
import com.han.system.sdfz.order.legacy.LegacyClassroomGateway;
import com.han.system.sdfz.order.legacy.LegacyCourse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在真实网关外面套一层，用来模拟「三课堂暂时不可用」与「个别课程一直失败」。
 *
 * <p>不打桩整个网关，是因为幂等与查重的正确性必须落在真的 SQL 上才算验证过。</p>
 */
public class FaultInjectingClassroomGateway implements LegacyClassroomGateway {

    private final LegacyClassroomGateway delegate;
    private final Set<String> failingCourseIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger materializeCalls = new AtomicInteger();

    private volatile boolean offline;
    private volatile boolean failuresRetryable = true;

    public FaultInjectingClassroomGateway(LegacyClassroomGateway delegate) {
        this.delegate = delegate;
    }

    public void reset() {
        failingCourseIds.clear();
        materializeCalls.set(0);
        offline = false;
        failuresRetryable = true;
    }

    /** 整条通道不可用，模拟旧库连不上。 */
    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    /** 指定课程物化失败，其它课程照常成功——这就是 ORDER-09 的「部分失败」。 */
    public void failCourse(String courseId) {
        failingCourseIds.add(courseId);
    }

    public void recoverCourse(String courseId) {
        failingCourseIds.remove(courseId);
    }

    public void setFailuresRetryable(boolean retryable) {
        this.failuresRetryable = retryable;
    }

    public int materializeCalls() {
        return materializeCalls.get();
    }

    @Override
    public List<LegacyCourse> listCourses(String lectureClassId, LocalDateTime from, LocalDateTime to) {
        requireOnline();
        return delegate.listCourses(lectureClassId, from, to);
    }

    @Override
    public LegacyCourse findCourse(String courseId) {
        requireOnline();
        return delegate.findCourse(courseId);
    }

    @Override
    public String materializeAttend(LegacyAttendRequest request) {
        requireOnline();
        materializeCalls.incrementAndGet();
        if (failingCourseIds.contains(request.courseId())) {
            throw failuresRetryable
                    ? LegacyClassroomException.retryable("注入的可重试故障", null)
                    : LegacyClassroomException.permanent("注入的不可重试故障");
        }
        return delegate.materializeAttend(request);
    }

    @Override
    public void revokeAttend(String courseId, String classId) {
        requireOnline();
        delegate.revokeAttend(courseId, classId);
    }

    @Override
    public boolean isAttendActive(String courseId, String classId) {
        requireOnline();
        return delegate.isAttendActive(courseId, classId);
    }

    private void requireOnline() {
        if (offline) {
            throw LegacyClassroomException.retryable("三课堂通道已下线（测试注入）", null);
        }
    }
}
