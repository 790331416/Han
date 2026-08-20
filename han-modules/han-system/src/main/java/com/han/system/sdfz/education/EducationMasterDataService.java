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
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduPersonSubjectMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.common.mybatis.helper.TenantHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
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

    private static final String DEVICE_TYPE_DICT = "edu_device_type";
    private static final String DEVICE_APPLICATION_DICT = "edu_device_application";
    private static final String ASSET_STATUS_DICT = "edu_asset_status";

    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduSubjectMapper subjectMapper;
    private final EduDeviceMapper deviceMapper;
    private final EduRoomMapper roomMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduPersonSubjectMapper personSubjectMapper;
    private final SysDictDataMapper dictDataMapper;
    private final EducationDataScopeService dataScopeService;

    // ---------------------------------------------------------------- 学校

    public PageResult<EduSchoolPo> listSchools(String keyword, Integer status, int pageNum, int pageSize) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (!scope.all() && scope.schoolIds().isEmpty()) {
            return emptyPage(pageNum, pageSize);
        }
        LambdaQueryWrapper<EduSchoolPo> query = new LambdaQueryWrapper<EduSchoolPo>()
                .eq(EduSchoolPo::getOrgType, "SCHOOL")
                .in(!scope.all(), EduSchoolPo::getId, scope.schoolIds())
                .eq(status != null, EduSchoolPo::getStatus, status)
                .and(notBlank(keyword), item -> item.like(EduSchoolPo::getSchoolCode, keyword)
                        .or().like(EduSchoolPo::getSchoolName, keyword))
                .orderByAsc(EduSchoolPo::getSchoolName);
        return page(schoolMapper, query, pageNum, pageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    public Long saveSchool(EducationForms.School form) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        EduSchoolPo school = form.id() == null ? new EduSchoolPo() : requireSchool(form.id());
        if (form.id() != null) {
            requireLocalSource(school.getSourceSystem(), "学校");
        }
        if (form.parentId() != null) {
            if (Objects.equals(form.parentId(), form.id())) {
                throw new BusinessException("学校上级不能是自身");
            }
            dataScopeService.requireOrganization(form.parentId());
            if (schoolMapper.selectById(form.parentId()) == null) {
                throw new BusinessException("上级教育组织不存在或不在当前数据范围");
            }
        } else if (form.id() == null && !scope.all()) {
            throw new BusinessException("当前数据范围不能创建根级学校");
        }
        String code = form.id() == null
                ? EducationCodeGenerator.unique("SCHOOL", form.schoolName(), candidate -> schoolMapper.selectCount(
                        new LambdaQueryWrapper<EduSchoolPo>().eq(EduSchoolPo::getSchoolCode, candidate)) > 0)
                : school.getSchoolCode();

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
            dataScopeService.requireSchool(school.getId());
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
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (schoolId != null) {
            dataScopeService.requireSchool(schoolId);
        } else if (!scope.all() && scope.schoolIds().isEmpty()) {
            return emptyPage(pageNum, pageSize);
        }
        LambdaQueryWrapper<EduClassPo> query = new LambdaQueryWrapper<EduClassPo>()
                .eq(schoolId != null, EduClassPo::getSchoolId, schoolId)
                .in(schoolId == null && !scope.all(), EduClassPo::getSchoolId, scope.schoolIds())
                .eq(EduClassPo::getNodeType, "CLASS")
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
        requireTeachingSchool(form.schoolId());
        EduClassPo item = form.id() == null ? new EduClassPo() : requireClass(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "班级");
        }
        String className = form.className().trim();
        String code = form.id() == null
                ? EducationCodeGenerator.unique("CLASS", className, candidate -> classMapper.selectCount(
                        new LambdaQueryWrapper<EduClassPo>()
                                .eq(EduClassPo::getSchoolId, form.schoolId())
                                .eq(EduClassPo::getClassCode, candidate)) > 0)
                : item.getClassCode();
        String gradeCode = form.id() == null
                ? EducationCodeGenerator.gradeCode(className)
                : item.getGradeCode();

        item.setSchoolId(form.schoolId());
        item.setGradeCode(gradeCode);
        item.setClassCode(code);
        item.setClassName(className);
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
            dataScopeService.requireSchool(item.getSchoolId());
            requireLocalSource(item.getSourceSystem(), "班级");
            rejectChildren(count(new LambdaQueryWrapper<EduClassPo>()
                    .eq(EduClassPo::getParentId, id), classMapper::selectCount), "教学组织", "下级节点");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonClassPo>()
                    .eq(EduPersonClassPo::getClassId, id), personClassMapper::selectCount), "班级", "归班人员");
            rejectChildren(count(new LambdaQueryWrapper<EduPersonSubjectPo>()
                    .eq(EduPersonSubjectPo::getClassId, id), personSubjectMapper::selectCount), "班级", "任教关系");
            removed += classMapper.deleteById(id);
        }
        return removed;
    }

    // ---------------------------------------------------------------- 科目

    public PageResult<EduSubjectPo> listSubjects(Long schoolId, String keyword, Integer status, int pageNum, int pageSize) {
        requireTenant();
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (schoolId != null) {
            requireTeachingSchool(schoolId);
        } else if (!scope.all() && scope.schoolIds().isEmpty()) {
            return emptyPage(pageNum, pageSize);
        }
        LambdaQueryWrapper<EduSubjectPo> query = new LambdaQueryWrapper<EduSubjectPo>()
                .eq(schoolId != null, EduSubjectPo::getSchoolId, schoolId)
                .in(schoolId == null && !scope.all(), EduSubjectPo::getSchoolId, scope.schoolIds())
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
        EduSchoolPo school = requireTeachingSchool(form.schoolId());
        EduSubjectPo item = form.id() == null ? new EduSubjectPo() : requireSubject(form.id());
        if (form.id() != null) {
            requireLocalSource(item.getSourceSystem(), "科目");
        }
        String subjectName = form.subjectName().trim();
        String schoolSeed = notBlank(school.getSchoolCode()) ? school.getSchoolCode() : school.getSchoolName();
        String code = item.getId() == null
                ? EducationCodeGenerator.unique("SUBJECT", schoolSeed + "_" + subjectName,
                candidate -> subjectMapper.selectCount(new LambdaQueryWrapper<EduSubjectPo>()
                        .eq(EduSubjectPo::getSchoolId, form.schoolId())
                        .eq(EduSubjectPo::getSubjectCode, candidate)) > 0)
                : item.getSubjectCode();

        item.setSchoolId(form.schoolId());
        item.setSubjectCode(code);
        item.setSubjectName(subjectName);
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
            dataScopeService.requireSchool(item.getSchoolId());
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
        EducationDataScopeService.Scope scope = dataScopeService.current();
        if (schoolId != null) {
            dataScopeService.requireSchool(schoolId);
        } else if (!scope.all() && scope.schoolIds().isEmpty()) {
            return emptyPage(pageNum, pageSize);
        }
        LambdaQueryWrapper<EduDevicePo> query = new LambdaQueryWrapper<EduDevicePo>()
                .eq(schoolId != null, EduDevicePo::getSchoolId, schoolId)
                .in(schoolId == null && !scope.all(), EduDevicePo::getSchoolId, scope.schoolIds())
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
        requireTeachingSchool(form.schoolId());
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

        String deviceType = requireEnabledDictValue(DEVICE_TYPE_DICT, form.deviceType(), "设备类型");
        List<String> applicationTypes = distinctValues(form.applicationTypes());
        for (String applicationType : applicationTypes) {
            requireEnabledDictValue(DEVICE_APPLICATION_DICT, applicationType, "设备应用类型");
            if (!applicationType.startsWith(deviceType + ":")) {
                throw new BusinessException("设备应用类型必须属于所选设备类型");
            }
        }
        if ("RECORDER".equals(deviceType) && applicationTypes.size() > 1) {
            throw new BusinessException("录播设备只能选择一个应用场景");
        }
        String assetStatus = requireEnabledDictValue(ASSET_STATUS_DICT, form.assetStatus(), "资产状态");

        item.setSchoolId(form.schoolId());
        item.setRoomId(form.roomId());
        item.setDeviceCode(code);
        item.setDeviceName(form.deviceName().trim());
        item.setDeviceType(deviceType);
        item.setApplicationTypes(String.join(",", applicationTypes));
        item.setModel(trimToNull(form.model()));
        item.setSerialNumber(trimToNull(form.serialNumber()));
        item.setAssetStatus(assetStatus);
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
            dataScopeService.requireSchool(item.getSchoolId());
            requireLocalSource(item.getSourceSystem(), "设备");
            removed += deviceMapper.deleteById(id);
        }
        return removed;
    }

    // 学期与教室的读写都在 EducationCalendarService：那一版带 lifecycle_status 三态与定时推进，
    // 是课程订购的依赖，本类这一份是它的子集，合并时整体退役，避免两条写入路径写同一张表。
    // 本类只保留 requireRoom / roomMapper，用于设备挂教室与删除学校时的下挂检查。

    // ---------------------------------------------------------------- 内部

    private EduSchoolPo requireSchool(Long id) {
        EduSchoolPo value = id != null ? schoolMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getId());
        return value;
    }

    private EduSchoolPo requireTeachingSchool(Long id) {
        EduSchoolPo value = requireSchool(id);
        if (!EducationSupport.isOperationalSchool(value)) {
            throw new BusinessException("教学数据只能归属校区或独立学校");
        }
        return value;
    }

    private EduClassPo requireClass(Long id) {
        EduClassPo value = id != null ? classMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("班级不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getSchoolId());
        return value;
    }

    private EduSubjectPo requireSubject(Long id) {
        EduSubjectPo value = id != null ? subjectMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("科目不存在或不在当前数据范围");
        }
        if (value.getSchoolId() == null) {
            throw new BusinessException("科目尚未关联学校，请先完成归属迁移");
        }
        dataScopeService.requireSchool(value.getSchoolId());
        return value;
    }

    private EduDevicePo requireDevice(Long id) {
        EduDevicePo value = id != null ? deviceMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("设备不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getSchoolId());
        return value;
    }

    private EduRoomPo requireRoom(Long id) {
        EduRoomPo value = id != null ? roomMapper.selectById(id) : null;
        if (value == null) {
            throw new BusinessException("教室不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(value.getSchoolId());
        return value;
    }

    private void requireRoomInSchool(Long roomId, Long schoolId) {
        EduRoomPo room = requireRoom(roomId);
        if (!Objects.equals(room.getSchoolId(), schoolId)) {
            throw new BusinessException("教室不属于所选学校");
        }
        if ("BUILDING".equals(room.getNodeType()) || "FLOOR".equals(room.getNodeType())) {
            throw new BusinessException("设备只能挂载到具体场所");
        }
    }

    private String requireEnabledDictValue(String dictType, String requested, String fieldName) {
        String value = trimToNull(requested);
        Long count = value == null ? 0L : dictDataMapper.selectCount(
                new LambdaQueryWrapper<SysDictDataPo>()
                        .eq(SysDictDataPo::getDictType, dictType)
                        .eq(SysDictDataPo::getDictValue, value)
                        .eq(SysDictDataPo::getStatus, 0));
        if (value != null && (count == null || count == 0)) {
            count = TenantHelper.ignore(() -> dictDataMapper.selectCount(new LambdaQueryWrapper<SysDictDataPo>()
                    .isNull(SysDictDataPo::getTenantId)
                    .eq(SysDictDataPo::getDictType, dictType)
                    .eq(SysDictDataPo::getDictValue, value)
                    .eq(SysDictDataPo::getStatus, 0)));
        }
        if (value == null || count == null || count == 0) {
            throw new BusinessException(fieldName + "不存在或已停用");
        }
        return value;
    }

    private static List<String> distinctValues(List<String> values) {
        if (values == null || values.isEmpty()) throw new BusinessException("请至少选择一个设备应用类型");
        Set<String> result = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized == null) throw new BusinessException("设备应用类型不能为空");
            result.add(normalized);
        }
        return List.copyOf(result);
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

    private static <T> PageResult<T> emptyPage(int pageNum, int pageSize) {
        int safePage = Math.max(pageNum, 1);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        return new PageResult<>(List.of(), 0, safePage, safeSize);
    }
}
