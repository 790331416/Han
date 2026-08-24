package com.han.system.sdfz.digitalcampus;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.api.system.domain.DigitalCampusUserSyncDTO;
import com.han.common.core.exception.BusinessException;
import com.han.common.mybatis.helper.TenantHelper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRegionPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduRegionMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将当前数字校园身份快照幂等写入教育主数据，不处理视频运行态数据。 */
@Service
@RequiredArgsConstructor
public class DigitalCampusEducationSyncService {

    private static final String SOURCE = "DIGITAL_CAMPUS";

    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduRegionMapper regionMapper;

    @Transactional(rollbackFor = Exception.class)
    public void sync(DigitalCampusUserSyncDTO dto, Long hanUserId) {
        TenantHelper.ignore(() -> doSync(dto, hanUserId));
    }

    private void doSync(DigitalCampusUserSyncDTO dto, Long hanUserId) {
        Map<String, SchoolSnapshot> schools = collectSchools(dto);
        if (schools.isEmpty()) {
            return;
        }

        Map<String, Long> schoolIds = new LinkedHashMap<>();
        schools.forEach((externalId, snapshot) ->
                schoolIds.put(externalId, upsertSchool(dto.getTenantId(), externalId, snapshot)));

        List<ClassSnapshot> classes = collectClasses(dto);
        Map<String, Long> classIds = new LinkedHashMap<>();
        for (ClassSnapshot snapshot : classes) {
            Long schoolId = schoolIds.get(snapshot.schoolExternalId());
            if (schoolId != null) {
                classIds.put(snapshot.externalId(), upsertClass(dto.getTenantId(), schoolId, snapshot));
            }
        }

        String primarySchoolExternalId = firstNonBlank(dto.getSchoolId(), schools.keySet().iterator().next());
        Long primarySchoolId = schoolIds.get(primarySchoolExternalId);
        if (primarySchoolId == null) {
            return;
        }

        EduPersonPo person = upsertPerson(dto, hanUserId, primarySchoolId);
        for (ClassSnapshot snapshot : classes) {
            Long classId = classIds.get(snapshot.externalId());
            if (classId != null) {
                upsertMembership(dto.getTenantId(), person.getId(), classId, snapshot.membershipRole());
            }
        }
    }

    private Map<String, SchoolSnapshot> collectSchools(DigitalCampusUserSyncDTO dto) {
        Map<String, SchoolSnapshot> result = new LinkedHashMap<>();
        addSchool(result, dto.getSchoolId(), dto.getSchoolName(), dto.getAreaCode(), "PRIMARY");
        for (DigitalCampusUserSyncDTO.ClassMembership item : safeList(dto.getClasses())) {
            addSchool(result, item.getSchoolId(), item.getSchoolName(), item.getAreaCode(), "NORMAL");
        }
        return result;
    }

    private void addSchool(Map<String, SchoolSnapshot> target, String externalId, String name,
                           String areaCode, String role) {
        if (isBlank(externalId)) return;
        target.merge(externalId, new SchoolSnapshot(display(name, externalId), trim(areaCode, 32), role),
                (oldValue, newValue) -> "PRIMARY".equals(oldValue.role()) ? oldValue : newValue);
    }

    private List<ClassSnapshot> collectClasses(DigitalCampusUserSyncDTO dto) {
        Map<String, ClassSnapshot> result = new LinkedHashMap<>();
        if (!isBlank(dto.getBranchId()) && !isBlank(dto.getSchoolId())) {
            result.put(dto.getBranchId(), new ClassSnapshot(dto.getBranchId(), dto.getSchoolId(),
                    display(dto.getBranchName(), dto.getBranchId()), "NORMAL"));
        }
        for (DigitalCampusUserSyncDTO.ClassMembership item : safeList(dto.getClasses())) {
            String schoolExternalId = firstNonBlank(item.getSchoolId(), dto.getSchoolId());
            if (isBlank(item.getBranchId()) || isBlank(schoolExternalId)) continue;
            result.put(item.getBranchId(), new ClassSnapshot(item.getBranchId(), schoolExternalId,
                    display(item.getBranchName(), item.getBranchId()),
                    display(item.getClassRoleId(), "NORMAL")));
        }
        return new ArrayList<>(result.values());
    }

    private Long upsertSchool(Long tenantId, String externalId, SchoolSnapshot snapshot) {
        externalId = trim(externalId, 128);
        EduSchoolPo value = schoolMapper.selectOne(new LambdaQueryWrapper<EduSchoolPo>()
                .eq(EduSchoolPo::getTenantId, tenantId)
                .eq(EduSchoolPo::getSourceSystem, SOURCE)
                .eq(EduSchoolPo::getExternalId, externalId)
                .last("LIMIT 1"));
        boolean creating = value == null;
        if (creating) value = new EduSchoolPo();
        EduRegionPo region = requireRegion(tenantId, snapshot.areaCode());
        value.setTenantId(tenantId);
        value.setSchoolCode(externalCode("dc_school_", externalId, 64));
        value.setSchoolName(trim(snapshot.name(), 128));
        value.setSchoolRole(trim(snapshot.role(), 16));
        value.setSourceSystem(SOURCE);
        value.setExternalId(trim(externalId, 128));
        value.setRegionId(region.getId());
        value.setAreaCode(region.getRegionCode());
        value.setSyncHash(hash(externalId, snapshot.name(), snapshot.areaCode(), snapshot.role()));
        value.setLastSyncTime(LocalDateTime.now());
        if (creating) {
            value.setStatus(0);
            schoolMapper.insert(value);
        } else {
            schoolMapper.updateById(value);
        }
        return value.getId();
    }

    private EduRegionPo requireRegion(Long tenantId, String areaCode) {
        String code = trim(areaCode, 32);
        if (isBlank(code)) {
            throw new BusinessException("数字校园学校缺少有效区域编码");
        }
        EduRegionPo region = regionMapper.selectOne(new LambdaQueryWrapper<EduRegionPo>()
                .eq(EduRegionPo::getTenantId, tenantId)
                .eq(EduRegionPo::getRegionCode, code)
                .eq(EduRegionPo::getStatus, 0)
                .last("LIMIT 1"));
        if (region == null) {
            throw new BusinessException("数字校园学校区域编码不存在: " + code);
        }
        return region;
    }

    private Long upsertClass(Long tenantId, Long schoolId, ClassSnapshot snapshot) {
        String externalId = trim(snapshot.externalId(), 128);
        EduClassPo value = classMapper.selectOne(new LambdaQueryWrapper<EduClassPo>()
                .eq(EduClassPo::getTenantId, tenantId)
                .eq(EduClassPo::getSourceSystem, SOURCE)
                .eq(EduClassPo::getExternalId, externalId)
                .last("LIMIT 1"));
        boolean creating = value == null;
        if (creating) value = new EduClassPo();
        value.setTenantId(tenantId);
        value.setSchoolId(schoolId);
        value.setClassCode(externalCode("dc_class_", externalId, 64));
        value.setClassName(trim(snapshot.name(), 128));
        value.setClassRole(trim(snapshot.membershipRole(), 16));
        value.setSourceSystem(SOURCE);
        value.setExternalId(externalId);
        value.setSyncHash(hash(externalId, snapshot.schoolExternalId(), snapshot.name(), snapshot.membershipRole()));
        value.setLastSyncTime(LocalDateTime.now());
        if (creating) {
            value.setStatus(0);
            classMapper.insert(value);
        } else {
            classMapper.updateById(value);
        }
        return value.getId();
    }

    private EduPersonPo upsertPerson(DigitalCampusUserSyncDTO dto, Long hanUserId, Long schoolId) {
        String externalUserId = trim(dto.getExternalUserId(), 128);
        String externalIdentityId = trim(dto.getExternalIdentityId(), 128);
        // 幂等键对齐 DB 唯一索引 uq_edu_person_external(tenant_id, source_system, external_identity_id)：
        // 一个外部身份就是一条独立 edu_person，不能把 external_user_id 掺进查询，
        // 否则同一身份换外部账号标识时会查不到存量行、插入撞唯一索引。
        EduPersonPo value = personMapper.selectOne(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getTenantId, dto.getTenantId())
                .eq(EduPersonPo::getSourceSystem, SOURCE)
                .eq(EduPersonPo::getExternalIdentityId, externalIdentityId)
                .last("LIMIT 1"));
        boolean creating = value == null;
        if (creating) value = new EduPersonPo();
        value.setTenantId(dto.getTenantId());
        value.setUserId(hanUserId);
        value.setSchoolId(schoolId);
        value.setPersonNo(externalCode("dc_person_", externalIdentityId, 64));
        value.setPersonName(trim(display(dto.getUserName(), dto.getExternalUserId()), 128));
        value.setPersonType(trim(display(dto.getRoleType(), display(dto.getIdentityName(), "USER")), 16));
        value.setPhone(normalPhone(dto.getPhone()));
        value.setSourceSystem(SOURCE);
        value.setExternalUserId(externalUserId);
        value.setExternalIdentityId(externalIdentityId);
        value.setSyncHash(hash(externalUserId, externalIdentityId, dto.getUserName(),
                dto.getRoleType(), String.valueOf(schoolId)));
        value.setLastSyncTime(LocalDateTime.now());
        if (creating) {
            value.setStatus(0);
            personMapper.insert(value);
        } else {
            personMapper.updateById(value);
        }
        return value;
    }

    private void upsertMembership(Long tenantId, Long personId, Long classId, String role) {
        String safeRole = trim(display(role, "NORMAL"), 32);
        EduPersonClassPo existing = personClassMapper.selectOne(new LambdaQueryWrapper<EduPersonClassPo>()
                .eq(EduPersonClassPo::getTenantId, tenantId)
                .eq(EduPersonClassPo::getPersonId, personId)
                .eq(EduPersonClassPo::getClassId, classId)
                .eq(EduPersonClassPo::getMembershipRole, safeRole)
                .last("LIMIT 1"));
        if (existing == null) {
            EduPersonClassPo value = new EduPersonClassPo();
            value.setTenantId(tenantId);
            value.setPersonId(personId);
            value.setClassId(classId);
            value.setMembershipRole(safeRole);
            value.setSourceSystem(SOURCE);
            personClassMapper.insert(value);
        }
    }

    private static String externalCode(String prefix, String externalId, int maxLength) {
        return (prefix + hash(externalId)).substring(0, maxLength);
    }

    private static String hash(String... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String value : values) {
                if (value != null) digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK missing SHA-256", e);
        }
    }

    private static String display(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private static String trim(String value, int maxLength) {
        if (value == null) return null;
        String result = value.trim();
        return result.length() <= maxLength ? result : result.substring(0, maxLength);
    }

    private static String normalPhone(String phone) {
        return phone != null && phone.matches("\\d{11}") ? phone : null;
    }

    private static String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : second;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private record SchoolSnapshot(String name, String areaCode, String role) { }
    private record ClassSnapshot(String externalId, String schoolExternalId, String name,
                                 String membershipRole) { }
}
