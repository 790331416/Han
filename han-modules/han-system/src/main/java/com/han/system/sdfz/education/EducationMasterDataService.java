package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 学校、班级、人员、科目和设备的最小管理服务。
 *
 * <p>不管理设备心跳、在线状态、课堂控制和视频字段。</p>
 */
@Service
@RequiredArgsConstructor
public class EducationMasterDataService {

    private static final String LOCAL_SOURCE = "HAN";

    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduSubjectMapper subjectMapper;
    private final EduDeviceMapper deviceMapper;

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
        requireLocalSource(school, form.id(), "学校");
        if (form.parentId() != null) {
            if (Objects.equals(form.parentId(), form.id())) {
                throw new BusinessException("学校上级不能是自身");
            }
            requireSchool(form.parentId());
        }
        school.setParentId(form.parentId());
        school.setSchoolCode(form.schoolCode().trim());
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
        requireLocalSource(item, form.id(), "班级");
        item.setSchoolId(form.schoolId());
        item.setGradeCode(trimToNull(form.gradeCode()));
        item.setClassCode(form.classCode().trim());
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

    public PageResult<EduPersonPo> listPeople(Long schoolId, String personType, String keyword,
                                               Integer status, int pageNum, int pageSize) {
        requireTenant();
        LambdaQueryWrapper<EduPersonPo> query = new LambdaQueryWrapper<EduPersonPo>()
                .eq(schoolId != null, EduPersonPo::getSchoolId, schoolId)
                .eq(notBlank(personType), EduPersonPo::getPersonType, personType)
                .eq(status != null, EduPersonPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduPersonPo::getPersonNo, keyword)
                        .or().like(EduPersonPo::getPersonName, keyword))
                .orderByAsc(EduPersonPo::getPersonName);
        return page(personMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long savePerson(EducationForms.Person form) {
        requireTenant();
        requireSchool(form.schoolId());
        EduPersonPo item = form.id() == null ? new EduPersonPo() : requirePerson(form.id());
        requireLocalSource(item, form.id(), "人员");
        item.setUserId(form.userId());
        item.setSchoolId(form.schoolId());
        item.setPersonNo(form.personNo().trim());
        item.setPersonName(form.personName().trim());
        item.setPersonType(form.personType().trim());
        item.setPhone(trimToNull(form.phone()));
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (form.id() == null) {
            item.setTenantId(requireTenant());
            item.setSourceSystem(LOCAL_SOURCE);
            personMapper.insert(item);
        } else {
            personMapper.updateById(item);
        }
        return item.getId();
    }

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
        item.setSubjectCode(form.subjectCode().trim());
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
        item.setSchoolId(form.schoolId());
        item.setRoomId(form.roomId());
        item.setDeviceCode(form.deviceCode().trim());
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

    private EduSchoolPo requireSchool(Long id) {
        EduSchoolPo value = id != null ? schoolMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        return value;
    }

    private EduClassPo requireClass(Long id) {
        EduClassPo value = id != null ? classMapper.selectById(id) : null;
        if (value == null) throw new BusinessException("班级不存在或不在当前数据范围");
        return value;
    }

    private EduPersonPo requirePerson(Long id) {
        EduPersonPo value = id != null ? personMapper.selectById(id) : null;
        if (value == null) throw new BusinessException("人员不存在或不在当前数据范围");
        return value;
    }

    private EduSubjectPo requireSubject(Long id) {
        EduSubjectPo value = id != null ? subjectMapper.selectById(id) : null;
        if (value == null) throw new BusinessException("科目不存在或不在当前数据范围");
        return value;
    }

    private EduDevicePo requireDevice(Long id) {
        EduDevicePo value = id != null ? deviceMapper.selectById(id) : null;
        if (value == null) throw new BusinessException("设备不存在或不在当前数据范围");
        return value;
    }

    private static void requireLocalSource(Object value, Long id, String name) {
        if (id == null) return;
        String source = switch (value) {
            case EduSchoolPo item -> item.getSourceSystem();
            case EduClassPo item -> item.getSourceSystem();
            case EduPersonPo item -> item.getSourceSystem();
            default -> LOCAL_SOURCE;
        };
        if (!LOCAL_SOURCE.equals(source)) {
            throw new BusinessException(name + "来自数字校园，请通过同步更新");
        }
    }

    private static int normalStatus(Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态只能是 0 或 1");
        }
        return status;
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

    private static <T> PageResult<T> page(BaseMapper<T> mapper, Wrapper<T> query,
                                           int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        Page<T> result = mapper.selectPage(new Page<>(safePage, safeSize), query);
        return new PageResult<>(result.getRecords(), result.getTotal(), safePage, safeSize);
    }
}
