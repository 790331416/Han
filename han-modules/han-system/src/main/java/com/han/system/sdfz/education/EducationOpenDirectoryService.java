package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.api.system.domain.EducationClassSummaryVO;
import com.han.api.system.domain.EducationDeviceDirectoryVO;
import com.han.api.system.domain.EducationPersonDirectoryVO;
import com.han.common.core.domain.PageResult;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduDeviceMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 已授权第三方应用所需的教育目录查询。
 *
 * <p>调用方必须传入已经由开放平台恢复出的租户和学校范围；空范围按拒绝处理，
 * 不从浏览器或第三方请求参数推断范围。</p>
 */
@Service
@RequiredArgsConstructor
public class EducationOpenDirectoryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduClassMapper classMapper;
    private final EduDeviceMapper deviceMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduRoomMapper roomMapper;

    public PageResult<EducationPersonDirectoryVO> people(Long tenantId, Collection<Long> schoolIds,
                                                          String personType, Integer status,
                                                          LocalDateTime updatedAfter, Integer pageNum, Integer pageSize) {
        Scope scope = scope(tenantId, schoolIds);
        int current = normalizePage(pageNum);
        int size = normalizeSize(pageSize);
        Page<EduPersonPo> page = personMapper.selectPage(new Page<>(current, size),
                tenantScoped(new LambdaQueryWrapper<EduPersonPo>(), scope.tenantId())
                        .in(EduPersonPo::getSchoolId, scope.schoolIds())
                        .eq(personType != null && !personType.isBlank(), EduPersonPo::getPersonType, personType)
                        .eq(status != null, EduPersonPo::getStatus, status)
                        .gt(updatedAfter != null, EduPersonPo::getUpdateTime, updatedAfter)
                        .orderByAsc(EduPersonPo::getId));
        List<EduPersonPo> persons = page.getRecords();
        Map<Long, EduSchoolPo> schools = schools(scope.tenantId(), persons.stream().map(EduPersonPo::getSchoolId).toList());
        Map<Long, List<EducationClassSummaryVO>> classes = classes(scope.tenantId(), persons.stream().map(EduPersonPo::getId).toList());
        List<EducationPersonDirectoryVO> rows = persons.stream().map(person -> new EducationPersonDirectoryVO(
                person.getId(), person.getPersonNo(), person.getPersonName(), person.getPersonType(), person.getDutyCode(),
                person.getSchoolId(), schoolName(schools.get(person.getSchoolId())),
                classes.getOrDefault(person.getId(), List.of()), person.getStatus(), person.getUpdateTime())).toList();
        return PageResult.of(rows, page.getTotal(), current, size);
    }

    public PageResult<EducationDeviceDirectoryVO> devices(Long tenantId, Collection<Long> schoolIds,
                                                           Integer status, LocalDateTime updatedAfter,
                                                           Integer pageNum, Integer pageSize) {
        Scope scope = scope(tenantId, schoolIds);
        int current = normalizePage(pageNum);
        int size = normalizeSize(pageSize);
        Page<EduDevicePo> page = deviceMapper.selectPage(new Page<>(current, size),
                tenantScoped(new LambdaQueryWrapper<EduDevicePo>(), scope.tenantId())
                        .in(EduDevicePo::getSchoolId, scope.schoolIds())
                        .eq(status != null, EduDevicePo::getStatus, status)
                        .gt(updatedAfter != null, EduDevicePo::getUpdateTime, updatedAfter)
                        .orderByAsc(EduDevicePo::getId));
        List<EduDevicePo> devices = page.getRecords();
        Map<Long, EduSchoolPo> schools = schools(scope.tenantId(), devices.stream().map(EduDevicePo::getSchoolId).toList());
        Map<Long, EduRoomPo> rooms = rooms(scope.tenantId(), devices.stream().map(EduDevicePo::getRoomId).toList());
        List<EducationDeviceDirectoryVO> rows = devices.stream().map(device -> {
            EduRoomPo room = rooms.get(device.getRoomId());
            return new EducationDeviceDirectoryVO(
                    device.getId(), device.getDeviceCode(), device.getDeviceName(), device.getDeviceType(),
                    split(device.getApplicationTypes()), device.getSchoolId(), schoolName(schools.get(device.getSchoolId())),
                    device.getRoomId(), room != null ? room.getRoomName() : "", device.getStatus(), device.getUpdateTime());
        }).toList();
        return PageResult.of(rows, page.getTotal(), current, size);
    }

    public EducationDeviceDirectoryVO device(Long tenantId, Collection<Long> schoolIds, String deviceCode) {
        Scope scope = scope(tenantId, schoolIds);
        if (deviceCode == null || deviceCode.isBlank()) {
            throw new BusinessException("设备编码不能为空");
        }
        EduDevicePo device = deviceMapper.selectOne(tenantScoped(new LambdaQueryWrapper<EduDevicePo>(), scope.tenantId())
                .eq(EduDevicePo::getDeviceCode, deviceCode.trim())
                .in(EduDevicePo::getSchoolId, scope.schoolIds())
                .eq(EduDevicePo::getDelFlag, 0)
                .last("LIMIT 1"));
        if (device == null) {
            return null;
        }
        EduSchoolPo school = schools(scope.tenantId(), List.of(device.getSchoolId())).get(device.getSchoolId());
        EduRoomPo room = device.getRoomId() == null ? null
                : rooms(scope.tenantId(), List.of(device.getRoomId())).get(device.getRoomId());
        return new EducationDeviceDirectoryVO(device.getId(), device.getDeviceCode(), device.getDeviceName(),
                device.getDeviceType(), split(device.getApplicationTypes()), device.getSchoolId(), schoolName(school),
                device.getRoomId(), room == null ? "" : room.getRoomName(), device.getStatus(), device.getUpdateTime());
    }

    /** 仅解析调用方已经授权的学校名称，未知 ID 不返回。 */
    public Map<Long, String> schoolNames(Long tenantId, Collection<Long> schoolIds) {
        Scope scope = scope(tenantId, schoolIds);
        Map<Long, EduSchoolPo> schools = schools(scope.tenantId(), scope.schoolIds());
        Map<Long, String> result = new LinkedHashMap<>();
        scope.schoolIds().forEach(id -> {
            EduSchoolPo school = schools.get(id);
            if (school != null) result.put(id, schoolName(school));
        });
        return result;
    }

    private Map<Long, List<EducationClassSummaryVO>> classes(Long tenantId, List<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        List<EduPersonClassPo> memberships = personClassMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduPersonClassPo>(), tenantId)
                .in(EduPersonClassPo::getPersonId, personIds)
                .eq(EduPersonClassPo::getMembershipStatus, "ACTIVE"));
        Map<Long, EduClassPo> classById = classesById(tenantId, memberships.stream().map(EduPersonClassPo::getClassId).toList());
        Map<Long, List<EducationClassSummaryVO>> result = new LinkedHashMap<>();
        for (EduPersonClassPo membership : memberships) {
            EduClassPo schoolClass = classById.get(membership.getClassId());
            if (schoolClass != null) {
                result.computeIfAbsent(membership.getPersonId(), ignored -> new java.util.ArrayList<>())
                        .add(new EducationClassSummaryVO(schoolClass.getId(), schoolClass.getClassName()));
            }
        }
        return result;
    }

    private Map<Long, EduClassPo> classesById(Long tenantId, List<Long> ids) {
        Set<Long> unique = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return classMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduClassPo>(), tenantId)
                        .in(EduClassPo::getId, unique)
                        .eq(EduClassPo::getStatus, 0))
                .stream().collect(Collectors.toMap(EduClassPo::getId, Function.identity()));
    }

    private Map<Long, EduSchoolPo> schools(Long tenantId, List<Long> ids) {
        Set<Long> unique = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return schoolMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduSchoolPo>(), tenantId)
                        .in(EduSchoolPo::getId, unique))
                .stream().collect(Collectors.toMap(EduSchoolPo::getId, Function.identity()));
    }

    private Map<Long, EduRoomPo> rooms(Long tenantId, List<Long> ids) {
        Set<Long> unique = ids.stream().filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (unique.isEmpty()) {
            return Map.of();
        }
        return roomMapper.selectList(tenantScoped(new LambdaQueryWrapper<EduRoomPo>(), tenantId)
                        .in(EduRoomPo::getId, unique))
                .stream().collect(Collectors.toMap(EduRoomPo::getId, Function.identity()));
    }

    private static <T> LambdaQueryWrapper<T> tenantScoped(LambdaQueryWrapper<T> query, Long tenantId) {
        return query.apply("tenant_id = {0}", tenantId);
    }

    private static Scope scope(Long tenantId, Collection<Long> schoolIds) {
        if (tenantId == null || tenantId <= 0 || schoolIds == null) {
            throw new BusinessException("开放目录缺少授权数据范围");
        }
        List<Long> normalized = schoolIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (normalized.isEmpty()) {
            throw new BusinessException("开放目录未授权任何学校");
        }
        return new Scope(tenantId, normalized);
    }

    private static int normalizePage(Integer value) {
        return value == null || value < 1 ? 1 : value;
    }

    private static int normalizeSize(Integer value) {
        return Math.min(Math.max(value == null ? 20 : value, 1), MAX_PAGE_SIZE);
    }

    private static List<String> split(String value) {
        return value == null || value.isBlank() ? List.of()
                : java.util.Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }

    private static String schoolName(EduSchoolPo school) {
        return school != null && school.getSchoolName() != null ? school.getSchoolName() : "";
    }

    private record Scope(Long tenantId, List<Long> schoolIds) {
    }
}
