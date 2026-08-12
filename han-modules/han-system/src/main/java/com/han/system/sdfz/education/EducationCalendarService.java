package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EducationCalendarForms;
import com.han.system.sdfz.education.domain.SemesterLifecycle;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 学期与教室管理服务。
 *
 * <p>两张表在 {@code 20260811_education_master.sql} 里就已经建好并灌了数据，但一直没有管理端入口，
 * 只能靠 SQL 维护。订购单必须绑学期、物化听课记录要取教室，所以这里补齐。</p>
 *
 * <p>与 {@link EducationMasterDataService} 分开成两个类，是为了不和正在返工中的主数据代码抢同一批文件。</p>
 */
@Service
@RequiredArgsConstructor
public class EducationCalendarService {

    private static final String LOCAL_SOURCE = "HAN";

    private final EduSemesterMapper semesterMapper;
    private final EduRoomMapper roomMapper;
    private final EduSchoolMapper schoolMapper;

    public PageResult<EduSemesterPo> listSemesters(String keyword, Integer status, String lifecycleStatus,
                                                   int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduSemesterPo> query = new LambdaQueryWrapper<EduSemesterPo>()
                .eq(status != null, EduSemesterPo::getStatus, status)
                .eq(notBlank(lifecycleStatus), EduSemesterPo::getLifecycleStatus, lifecycleStatus)
                .and(notBlank(keyword), item -> item.like(EduSemesterPo::getSemesterCode, keyword)
                        .or().like(EduSemesterPo::getSemesterName, keyword))
                .orderByDesc(EduSemesterPo::getBeginDate);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<EduSemesterPo> result = semesterMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSemester(EducationCalendarForms.Semester form) {
        Long tenantId = requireTenant();
        if (form.endDate().isBefore(form.beginDate())) {
            throw new BusinessException("学期结束日期不能早于开始日期");
        }
        EduSemesterPo item = form.id() == null ? new EduSemesterPo() : requireSemester(form.id());
        item.setSemesterCode(form.semesterCode().trim());
        item.setSemesterName(form.semesterName().trim());
        item.setBeginDate(form.beginDate());
        item.setEndDate(form.endDate());
        item.setCurrentFlag(flag(form.currentFlag()));
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        item.setLifecycleStatus(SemesterLifecycle.of(form.beginDate(), form.endDate(), LocalDate.now()).name());
        if (form.id() == null) {
            item.setTenantId(tenantId);
            semesterMapper.insert(item);
        } else {
            semesterMapper.updateById(item);
        }
        if (item.getCurrentFlag() != null && item.getCurrentFlag() == 1) {
            clearOtherCurrentFlags(item.getId());
        }
        return item.getId();
    }

    /**
     * 按当天日期推进全部学期的阶段，供定时任务调用。返回实际发生变更的行数。
     *
     * <p>幂等：已经处在正确阶段的行不会被更新。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public int advanceSemesterLifecycle(LocalDate today) {
        List<EduSemesterPo> semesters = semesterMapper.selectList(new LambdaQueryWrapper<EduSemesterPo>()
                .eq(EduSemesterPo::getStatus, 0));
        int changed = 0;
        for (EduSemesterPo semester : semesters) {
            String expected = SemesterLifecycle.of(semester.getBeginDate(), semester.getEndDate(), today).name();
            if (expected.equals(semester.getLifecycleStatus())) {
                continue;
            }
            EduSemesterPo update = new EduSemesterPo();
            update.setId(semester.getId());
            update.setLifecycleStatus(expected);
            semesterMapper.updateById(update);
            changed++;
        }
        return changed;
    }

    public PageResult<EduRoomPo> listRooms(Long schoolId, String keyword, Integer status,
                                           int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduRoomPo> query = new LambdaQueryWrapper<EduRoomPo>()
                .eq(schoolId != null, EduRoomPo::getSchoolId, schoolId)
                .eq(status != null, EduRoomPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduRoomPo::getRoomCode, keyword)
                        .or().like(EduRoomPo::getRoomName, keyword))
                .orderByAsc(EduRoomPo::getSchoolId)
                .orderByAsc(EduRoomPo::getRoomName);
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<EduRoomPo> result = roomMapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveRoom(EducationCalendarForms.Room form) {
        Long tenantId = requireTenant();
        if (schoolMapper.selectById(form.schoolId()) == null) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        EduRoomPo item = form.id() == null ? new EduRoomPo() : requireRoom(form.id());
        if (form.id() != null && !LOCAL_SOURCE.equals(item.getSourceSystem())) {
            throw new BusinessException("教室来自数字校园，请通过同步更新");
        }
        item.setSchoolId(form.schoolId());
        item.setRoomCode(form.roomCode().trim());
        item.setRoomName(form.roomName().trim());
        item.setRoomType(trimToNull(form.roomType()));
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(tenantId);
            item.setSourceSystem(LOCAL_SOURCE);
            roomMapper.insert(item);
        } else {
            roomMapper.updateById(item);
        }
        return item.getId();
    }

    private void clearOtherCurrentFlags(Long keepId) {
        semesterMapper.update(null, new LambdaUpdateWrapper<EduSemesterPo>()
                .ne(EduSemesterPo::getId, keepId)
                .eq(EduSemesterPo::getCurrentFlag, 1)
                .set(EduSemesterPo::getCurrentFlag, 0));
    }

    private EduSemesterPo requireSemester(Long id) {
        EduSemesterPo value = id != null ? semesterMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("学期不存在或不在当前数据范围");
        }
        return value;
    }

    private EduRoomPo requireRoom(Long id) {
        EduRoomPo value = id != null ? roomMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("教室不存在或不在当前数据范围");
        }
        return value;
    }

    private static int normalStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 0 或 1");
        }
        return status;
    }

    private static int flag(Integer value) {
        if (value == null || (value != 0 && value != 1)) {
            throw new BusinessException("是否当前学期只能是 0 或 1");
        }
        return value;
    }

    private static Long requireTenant() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文");
        }
        return tenantId;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String trimToNull(String value) {
        return notBlank(value) ? value.trim() : null;
    }
}
