package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduPersonSubjectPo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSemesterPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSemesterMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import static com.han.system.sdfz.education.EducationSupport.LOCAL_SOURCE;
import static com.han.system.sdfz.education.EducationSupport.normalStatus;
import static com.han.system.sdfz.education.EducationSupport.notBlank;
import static com.han.system.sdfz.education.EducationSupport.page;
import static com.han.system.sdfz.education.EducationSupport.rejectDuplicate;
import static com.han.system.sdfz.education.EducationSupport.requireLocalSource;
import static com.han.system.sdfz.education.EducationSupport.requireTenant;
import static com.han.system.sdfz.education.EducationSupport.trimToNull;

/**
 * 学校、班级、科目、设备、学期和教室的管理服务。
 *
 * <p>不管理设备心跳、在线状态、课堂控制和视频字段。人员及其账号由
 * {@link EducationPersonService} 统一维护。</p>
 *
 * <p>删除为逻辑删除：存在有效下级时拒绝；已不存在的 ID 直接跳过，保证重试与批量删除幂等。
 * 业务编码保持原值不改写——唯一索引已按 {@code del_flag} 生成列排除墓碑行，
 * 见 {@code sql/sdfz/mysql/20260812b_education_active_unique_index.sql}。</p>
 */
@Service
@RequiredArgsConstructor
public class EducationMasterDataService {

    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduSubjectMapper subjectMapper;
    private final EduDeviceMapper deviceMapper;
    private final EduSemesterMapper semesterMapper;
    private final EduRoomMapper roomMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduPersonSubjectMapper personSubjectMapper;

    // ---------------------------------------------------------------- 学校

    public PageResult<EduSchoolPo> listSchools(String keyword, Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduSchoolPo> query = new LambdaQueryWrapper<EduSchoolPo>()
                .eq(status != null, EduSchoolPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduSchoolPo::getSchoolCode, keyword)
                        .or().like(EduSchoolPo::getSchoolName, keyword))
                .orderByAsc(EduSchoolPo::getSchoolName);
        return page(schoolMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSchool(EducationForms.School form) {
        requireTenant();
        EduSchoolPo school = form.id() == null ? new EduSchoolPo() : requireSchool(form.id());
        if (form.id() != null) {
            requireLocalSource(school.getSourceSystem(), "学校");
        }
        if (form.parentId() != null) {
            if (Objects.equals(form.parentId(), form.id())) {
                throw new BusinessException("学校上级不能是自身");
            }
            requireSchool(form.parentId());
        }
        String code = form.schoolCode().trim();
        rejectDuplicate(schoolMapper.selectCount(new LambdaQueryWrapper<EduSchoolPo>()
                .eq(EduSchoolPo::getSchoolCode, code)
                .ne(form.id() != null, EduSchoolPo::getId, form.id())), "学校编码", code);

        school.setParentId(form.parentId());
        school.setSchoolCode(code);
        school.setSchoolName(form.schoolName().trim());
        school.setSchoolRole(form.schoolRole().trim());
        school.setAreaCode(trimToNull(form.areaCode()));
        school.setStatus(normalStatus(form.status()));
        school.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            school.setTenantId(requireTenant());
            school.setSourceSystem(LOCAL_SOURCE);
            schoolMapper.insert(school);
        } else {
            schoolMapper.updateById(school);
        }
        return school.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteSchools(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduSchoolPo school = schoolMapper.selectById(id);
            if (school == null) {
                continue;
            }
            requireLocalSource(school.getSourceSystem(), "学校");
            rejectChildren(count(new LambdaQueryWrapper<EduSchoolPo>()
                    .eq(EduSchoolPo::getParentId, id), schoolMapper::selectCount), "学校", "下级学校");
            rejectChildren(count(new LambdaQueryWrapper<EduClassPo>()
                    .eq(EduClassPo::getSchoolId, id), classMapper::selectCount), "学校", "班级");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonPo>()
                    .eq(EduPersonPo::getSchoolId, id), personMapper::selectCount), "学校", "人员");
            rejectChildren(count(new LambdaQueryWrapper<EduRoomPo>()
                    .eq(EduRoomPo::getSchoolId, id), roomMapper::selectCount), "学校", "教室");
            rejectChildren(count(new LambdaQueryWrapper<EduDevicePo>()
                    .eq(EduDevicePo::getSchoolId, id), deviceMapper::selectCount), "学校", "设备");
            removed += schoolMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 班级

    public PageResult<EduClassPo> listClasses(Long schoolId, String keyword, Integer status,
                                              int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduClassPo> query = new LambdaQueryWrapper<EduClassPo>()
                .eq(schoolId != null, EduClassPo::getSchoolId, schoolId)
                .eq(status != null, EduClassPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduClassPo::getClassCode, keyword)
                        .or().like(EduClassPo::getClassName, keyword))
                .orderByAsc(EduClassPo::getSchoolId)
                .orderByAsc(EduClassPo::getClassName);
        return page(classMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveClass(EducationForms.ClassInfo form) {
        requireTenant();
        requireSchool(form.schoolId());
        EduClassPo item = form.id() == null ? new EduClassPo() : requireClass(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "班级");
        }
        String code = form.classCode().trim();
        rejectDuplicate(classMapper.selectCount(new LambdaQueryWrapper<EduClassPo>()
                .eq(EduClassPo::getSchoolId, form.schoolId())
                .eq(EduClassPo::getClassCode, code)
                .ne(form.id() != null, EduClassPo::getId, form.id())), "班级编码", code);

        item.setSchoolId(form.schoolId());
        item.setGradeCode(trimToNull(form.gradeCode()));
        item.setClassCode(code);
        item.setClassName(form.className().trim());
        item.setClassRole(form.classRole().trim());
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            classMapper.insert(item);
        } else {
            classMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteClasses(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduClassPo item = classMapper.selectById(id);
            if (item == null) {
                continue;
            }
            requireLocalSource(item.getSourceSystem(), "班级");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonClassPo>()
                    .eq(EduPersonClassPo::getClassId, id), personClassMapper::selectCount), "班级", "归班人员");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonSubjectPo>()
                    .eq(EduPersonSubjectPo::getClassId, id), personSubjectMapper::selectCount), "班级", "任教关系");
            removed += classMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 科目

    public PageResult<EduSubjectPo> listSubjects(String keyword, Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduSubjectPo> query = new LambdaQueryWrapper<EduSubjectPo>()
                .eq(status != null, EduSubjectPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduSubjectPo::getSubjectCode, keyword)
                        .or().like(EduSubjectPo::getSubjectName, keyword))
                .orderByAsc(EduSubjectPo::getSort)
                .orderByAsc(EduSubjectPo::getSubjectName);
        return page(subjectMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSubject(EducationForms.Subject form) {
        requireTenant();
        EduSubjectPo item = form.id() == null ? new EduSubjectPo() : requireSubject(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "科目");
        }
        String code = form.subjectCode().trim();
        rejectDuplicate(subjectMapper.selectCount(new LambdaQueryWrapper<EduSubjectPo>()
                .eq(EduSubjectPo::getSubjectCode, code)
                .ne(form.id() != null, EduSubjectPo::getId, form.id())), "科目编码", code);

        item.setSubjectCode(code);
        item.setSubjectName(form.subjectName().trim());
        item.setSort(form.sort());
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            subjectMapper.insert(item);
        } else {
            subjectMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteSubjects(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduSubjectPo item = subjectMapper.selectById(id);
            if (item == null) {
                continue;
            }
            requireLocalSource(item.getSourceSystem(), "科目");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonSubjectPo>()
                    .eq(EduPersonSubjectPo::getSubjectId, id), personSubjectMapper::selectCount), "科目", "任教关系");
            removed += subjectMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 设备

    public PageResult<EduDevicePo> listDevices(Long schoolId, Long roomId, String keyword,
                                               Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduDevicePo> query = new LambdaQueryWrapper<EduDevicePo>()
                .eq(schoolId != null, EduDevicePo::getSchoolId, schoolId)
                .eq(roomId != null, EduDevicePo::getRoomId, roomId)
                .eq(status != null, EduDevicePo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduDevicePo::getDeviceCode, keyword)
                        .or().like(EduDevicePo::getDeviceName, keyword)
                        .or().like(EduDevicePo::getSerialNumber, keyword))
                .orderByAsc(EduDevicePo::getDeviceName);
        return page(deviceMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveDevice(EducationForms.Device form) {
        requireTenant();
        requireSchool(form.schoolId());
        EduDevicePo item = form.id() == null ? new EduDevicePo() : requireDevice(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "设备");
        }
        if (form.roomId() != null) {
            requireRoomInSchool(form.roomId(), form.schoolId());
        }
        String code = form.deviceCode().trim();
        rejectDuplicate(deviceMapper.selectCount(new LambdaQueryWrapper<EduDevicePo>()
                .eq(EduDevicePo::getDeviceCode, code)
                .ne(form.id() != null, EduDevicePo::getId, form.id())), "设备编码", code);

        item.setSchoolId(form.schoolId());
        item.setRoomId(form.roomId());
        item.setDeviceCode(code);
        item.setDeviceName(form.deviceName().trim());
        item.setDeviceType(form.deviceType().trim());
        item.setModel(trimToNull(form.model()));
        item.setSerialNumber(trimToNull(form.serialNumber()));
        item.setAssetStatus(form.assetStatus().trim());
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            deviceMapper.insert(item);
        } else {
            deviceMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteDevices(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduDevicePo item = deviceMapper.selectById(id);
            if (item == null) {
                continue;
            }
            requireLocalSource(item.getSourceSystem(), "设备");
            removed += deviceMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 学期

    public PageResult<EduSemesterPo> listSemesters(String keyword, Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduSemesterPo> query = new LambdaQueryWrapper<EduSemesterPo>()
                .eq(status != null, EduSemesterPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduSemesterPo::getSemesterCode, keyword)
                        .or().like(EduSemesterPo::getSemesterName, keyword))
                .orderByDesc(EduSemesterPo::getBeginDate);
        return page(semesterMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSemester(EducationForms.Semester form) {
        Long tenantId = requireTenant();
        if (form.endDate().isBefore(form.beginDate())) {
            throw new BusinessException("学期结束日期不能早于开始日期");
        }
        EduSemesterPo item = form.id() == null ? new EduSemesterPo() : requireSemester(form.id());
        String code = form.semesterCode().trim();
        rejectDuplicate(semesterMapper.selectCount(new LambdaQueryWrapper<EduSemesterPo>()
                .eq(EduSemesterPo::getSemesterCode, code)
                .ne(form.id() != null, EduSemesterPo::getId, form.id())), "学期编码", code);

        item.setSemesterCode(code);
        item.setSemesterName(form.semesterName().trim());
        item.setBeginDate(form.beginDate());
        item.setEndDate(form.endDate());
        item.setCurrentFlag(normalStatus(form.currentFlag()));
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));

        // 先让并发事务在同一批行上排队，再写自身，避免两笔都认为"已清理完别人"。
        if (item.getCurrentFlag() == 1) {
            clearOtherCurrentSemesters(form.id());
        }
        if (form.id() == null) {
            item.setTenantId(tenantId);
            semesterMapper.insert(item);
        } else {
            semesterMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteSemesters(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            if (semesterMapper.selectById(id) == null) {
                continue;
            }
            removed += semesterMapper.deleteById(id);
        }
        return removed;
    }

    /** 当前学期唯一：置位前把同租户其他学期的标记一次性清掉。 */
    private void clearOtherCurrentSemesters(Long keepId) {
        EduSemesterPo patch = new EduSemesterPo();
        patch.setCurrentFlag(0);
        semesterMapper.update(patch, new LambdaQueryWrapper<EduSemesterPo>()
                .eq(EduSemesterPo::getCurrentFlag, 1)
                .ne(keepId != null, EduSemesterPo::getId, keepId));
    }

    // ---------------------------------------------------------------- 教室

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
        return page(roomMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveRoom(EducationForms.Room form) {
        requireTenant();
        requireSchool(form.schoolId());
        EduRoomPo item = form.id() == null ? new EduRoomPo() : requireRoom(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "教室");
        }
        String code = form.roomCode().trim();
        rejectDuplicate(roomMapper.selectCount(new LambdaQueryWrapper<EduRoomPo>()
                .eq(EduRoomPo::getSchoolId, form.schoolId())
                .eq(EduRoomPo::getRoomCode, code)
                .ne(form.id() != null, EduRoomPo::getId, form.id())), "教室编码", code);

        item.setSchoolId(form.schoolId());
        item.setRoomCode(code);
        item.setRoomName(form.roomName().trim());
        item.setRoomType(trimToNull(form.roomType()));
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            roomMapper.insert(item);
        } else {
            roomMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteRooms(List<Long> ids) {
        requireTenant();
        int removed = 0;
        for (Long id : distinct(ids)) {
            EduRoomPo item = roomMapper.selectById(id);
            if (item == null) {
                continue;
            }
            requireLocalSource(item.getSourceSystem(), "教室");
            rejectChildren(count(new LambdaQueryWrapper<EduDevicePo>()
                    .eq(EduDevicePo::getRoomId, id), deviceMapper::selectCount), "教室", "设备");
            removed += roomMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 内部

    private EduSchoolPo requireSchool(Long id) {
        EduSchoolPo value = id != null ? schoolMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        return value;
    }

    private EduClassPo requireClass(Long id) {
        EduClassPo value = id != null ? classMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("班级不存在或不在当前数据范围");
        }
        return value;
    }

    private EduSubjectPo requireSubject(Long id) {
        EduSubjectPo value = id != null ? subjectMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("科目不存在或不在当前数据范围");
        }
        return value;
    }

    private EduDevicePo requireDevice(Long id) {
        EduDevicePo value = id != null ? deviceMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("设备不存在或不在当前数据范围");
        }
        return value;
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

    private void requireRoomInSchool(Long roomId, Long schoolId) {
        EduRoomPo room = requireRoom(roomId);
        if (!Objects.equals(room.getSchoolId(), schoolId)) {
            throw new BusinessException("教室不属于所选学校");
        }
    }

    private static void rejectChildren(long children, String owner, String childName) {
        if (children > 0) {
            throw new BusinessException("该" + owner + "下仍有 " + children + " 条" + childName + "，请先处理后再删除");
        }
    }

    private static <T> long count(LambdaQueryWrapper<T> query, Function<LambdaQueryWrapper<T>, Long> counter) {
        Long value = counter.apply(query);
        return value == null ? 0L : value;
    }

    private static List<Long> distinct(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        return ids.stream().filter(Objects::nonNull).distinct().toList();
    }
}
