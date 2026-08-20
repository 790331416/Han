package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.common.core.util.ClassroomTokenCodec;
import com.han.common.security.context.SecurityContextHolder;
import com.han.system.sdfz.education.domain.AcademicYearStatus;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduGradePromotionBatchPo;
import com.han.system.sdfz.education.domain.EduGradePromotionItemPo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationPromotionForms;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduGradePromotionBatchMapper;
import com.han.system.sdfz.education.mapper.EduGradePromotionItemMapper;
import com.han.system.sdfz.education.mapper.EduPersonClassMapper;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 学年升级只复制学生的学年归班关系，不修改原班级、原关系或课程订单。
 * 管理员先创建预览批次，再确认执行；映射全由请求明确给出，不按班级名称猜测。
 */
@Service
@RequiredArgsConstructor
public class EducationPromotionService {
    private static final String CLASS = "CLASS";
    private static final String STUDENT = "STUDENT";
    private static final String ACTIVE = "ACTIVE";
    private static final String COMPLETED = "COMPLETED";
    private static final String DRAFT = "DRAFT";
    private static final String EXECUTING = "EXECUTING";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String PARTIAL = "PARTIAL";
    private static final String PENDING = "PENDING";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";
    private static final String PROMOTE = "PROMOTE";
    private static final String GRADUATE = "GRADUATE";

    private final EduAcademicYearMapper academicYearMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduClassMapper classMapper;
    private final EduPersonMapper personMapper;
    private final EduPersonClassMapper personClassMapper;
    private final EduGradePromotionBatchMapper batchMapper;
    private final EduGradePromotionItemMapper itemMapper;
    private final EducationDataScopeService dataScopeService;

    /** 创建或读回同一套映射的预览批次；不会写人员归班关系。 */
    @Transactional(rollbackFor = Exception.class)
    public EduGradePromotionBatchPo preview(EducationPromotionForms.Preview form) {
        Long tenantId = requireTenant();
        requireTeachingSchool(form.schoolId());
        if (Objects.equals(form.sourceAcademicYearId(), form.targetAcademicYearId())) {
            throw new BusinessException("来源学年和目标学年不能相同");
        }
        requireSourceYear(form.sourceAcademicYearId());
        requireTargetYear(form.targetAcademicYearId());

        Map<Long, Mapping> mappings = validateMappings(form);
        String key = idempotencyKey(form, mappings);
        EduGradePromotionBatchPo existing = batchMapper.selectOne(new LambdaQueryWrapper<EduGradePromotionBatchPo>()
                .eq(EduGradePromotionBatchPo::getIdempotencyKey, key).last("limit 1"));
        if (existing != null) {
            return existing;
        }

        List<EduClassPo> sourceClasses = classMapper.selectList(new LambdaQueryWrapper<EduClassPo>()
                .eq(EduClassPo::getSchoolId, form.schoolId())
                .eq(EduClassPo::getAcademicYearId, form.sourceAcademicYearId())
                .eq(EduClassPo::getNodeType, CLASS)
                .eq(EduClassPo::getStatus, 0));
        Set<Long> sourceIds = sourceClasses.stream().map(EduClassPo::getId).collect(java.util.stream.Collectors.toSet());
        if (!sourceIds.equals(mappings.keySet())) {
            throw new BusinessException("班级映射必须覆盖来源学年的全部有效行政班，且不能包含其他班级");
        }
        Map<Long, EduClassPo> targets = targetClasses(form.schoolId(), form.targetAcademicYearId());
        for (Mapping mapping : mappings.values()) {
            if (PROMOTE.equals(mapping.action())) {
                EduClassPo target = targets.get(mapping.targetClassId());
                if (target == null) {
                    throw new BusinessException("目标班级不存在、已停用或不属于目标学年");
                }
            }
        }

        EduGradePromotionBatchPo batch = new EduGradePromotionBatchPo();
        batch.setTenantId(tenantId);
        batch.setSchoolId(form.schoolId());
        batch.setSourceAcademicYearId(form.sourceAcademicYearId());
        batch.setTargetAcademicYearId(form.targetAcademicYearId());
        batch.setStatus(DRAFT);
        batch.setIdempotencyKey(key);
        batch.setVersion(0);
        batch.setSuccessCount(0);
        batch.setFailedCount(0);
        batch.setRemark(trimToNull(form.remark()));
        batchMapper.insert(batch);

        int total = 0;
        Set<Long> personIds = new HashSet<>();
        for (EduClassPo source : sourceClasses) {
            Mapping mapping = mappings.get(source.getId());
            for (EduPersonClassPo membership : activeMemberships(source.getId(), form.sourceAcademicYearId())) {
                EduPersonPo person = personMapper.selectById(membership.getPersonId());
                if (person == null || !STUDENT.equals(person.getPersonType())) {
                    continue;
                }
                if (!personIds.add(membership.getPersonId())) {
                    throw new BusinessException("学生存在多个来源班级归属，请先修复历史数据后再升级");
                }
                EduGradePromotionItemPo item = new EduGradePromotionItemPo();
                item.setTenantId(tenantId);
                item.setBatchId(batch.getId());
                item.setPersonId(person.getId());
                item.setSourceClassId(source.getId());
                item.setTargetClassId(mapping.targetClassId());
                item.setAction(mapping.action());
                item.setResultStatus(PENDING);
                itemMapper.insert(item);
                total++;
            }
        }
        batch.setTotalCount(total);
        batchMapper.updateById(batch);
        return batch;
    }

    /** 执行已预览批次；重复确认只会重试 FAILED 项，SUCCESS 项不会重复创建归班关系。 */
    @Transactional(rollbackFor = Exception.class)
    public EduGradePromotionBatchPo confirm(Long batchId) {
        requireTenant();
        EduGradePromotionBatchPo batch = requireBatch(batchId);
        requireTeachingSchool(batch.getSchoolId());
        if (!DRAFT.equals(batch.getStatus()) && !PARTIAL.equals(batch.getStatus())) {
            throw new BusinessException("升级批次当前不能确认：" + batch.getStatus());
        }
        int claimed = batchMapper.update(null, new LambdaUpdateWrapper<EduGradePromotionBatchPo>()
                .eq(EduGradePromotionBatchPo::getId, batchId)
                .in(EduGradePromotionBatchPo::getStatus, DRAFT, PARTIAL)
                .set(EduGradePromotionBatchPo::getStatus, EXECUTING));
        if (claimed != 1) {
            throw new BusinessException("升级批次正在由其他操作执行，请刷新后重试");
        }

        int success = 0;
        int failed = 0;
        for (EduGradePromotionItemPo item : itemMapper.selectList(new LambdaQueryWrapper<EduGradePromotionItemPo>()
                .eq(EduGradePromotionItemPo::getBatchId, batchId))) {
            if (SUCCESS.equals(item.getResultStatus())) {
                success++;
                continue;
            }
            try {
                applyItem(batch, item);
                item.setResultStatus(SUCCESS);
                item.setErrorMessage(null);
                success++;
            } catch (BusinessException e) {
                item.setResultStatus(FAILED);
                item.setErrorMessage(trimToNull(e.getMessage()));
                failed++;
            }
            itemMapper.updateById(item);
        }
        batch.setStatus(failed == 0 ? CONFIRMED : PARTIAL);
        batch.setSuccessCount(success);
        batch.setFailedCount(failed);
        batch.setConfirmedBy(SecurityContextHolder.getUserId());
        batch.setConfirmedAt(LocalDateTime.now());
        batchMapper.updateById(batch);
        return batch;
    }

    public EduGradePromotionBatchPo detail(Long batchId) {
        requireTenant();
        EduGradePromotionBatchPo batch = requireBatch(batchId);
        requireTeachingSchool(batch.getSchoolId());
        return batch;
    }

    public List<EduGradePromotionBatchPo> list(Long schoolId) {
        requireTenant();
        requireTeachingSchool(schoolId);
        return batchMapper.selectList(new LambdaQueryWrapper<EduGradePromotionBatchPo>()
                .eq(EduGradePromotionBatchPo::getSchoolId, schoolId)
                .orderByDesc(EduGradePromotionBatchPo::getCreateTime));
    }

    private void applyItem(EduGradePromotionBatchPo batch, EduGradePromotionItemPo item) {
        EduPersonClassPo source = personClassMapper.selectOne(new LambdaQueryWrapper<EduPersonClassPo>()
                .eq(EduPersonClassPo::getPersonId, item.getPersonId())
                .eq(EduPersonClassPo::getClassId, item.getSourceClassId())
                .eq(EduPersonClassPo::getAcademicYearId, batch.getSourceAcademicYearId())
                .eq(EduPersonClassPo::getMembershipStatus, ACTIVE).last("limit 1"));
        if (source == null) {
            throw new BusinessException("来源学年归班关系不存在或已处理");
        }
        if (PROMOTE.equals(item.getAction())) {
            boolean targetExists = personClassMapper.selectCount(new LambdaQueryWrapper<EduPersonClassPo>()
                    .eq(EduPersonClassPo::getPersonId, item.getPersonId())
                    .eq(EduPersonClassPo::getClassId, item.getTargetClassId())
                    .eq(EduPersonClassPo::getAcademicYearId, batch.getTargetAcademicYearId())
                    .eq(EduPersonClassPo::getMembershipStatus, ACTIVE)) > 0;
            if (!targetExists) {
                EduPersonClassPo target = new EduPersonClassPo();
                target.setTenantId(requireTenant());
                target.setPersonId(item.getPersonId());
                target.setClassId(item.getTargetClassId());
                target.setAcademicYearId(batch.getTargetAcademicYearId());
                target.setMembershipRole(source.getMembershipRole());
                target.setMembershipStatus(ACTIVE);
                target.setEffectiveStartAt(LocalDateTime.now());
                target.setPromotionBatchId(batch.getId());
                target.setSourceSystem("HAN");
                personClassMapper.insert(target);
            }
        } else if (!GRADUATE.equals(item.getAction())) {
            throw new BusinessException("不支持的升级动作：" + item.getAction());
        }
        source.setMembershipStatus(COMPLETED);
        source.setEffectiveEndAt(LocalDateTime.now());
        source.setPromotionBatchId(batch.getId());
        personClassMapper.updateById(source);
    }

    private Map<Long, Mapping> validateMappings(EducationPromotionForms.Preview form) {
        Map<Long, Mapping> values = new HashMap<>();
        for (EducationPromotionForms.ClassMapping item : form.mappings()) {
            String action = item.action().trim().toUpperCase(Locale.ROOT);
            if (!PROMOTE.equals(action) && !GRADUATE.equals(action)) {
                throw new BusinessException("升级动作只能是 PROMOTE 或 GRADUATE");
            }
            if (PROMOTE.equals(action) && item.targetClassId() == null) {
                throw new BusinessException("升学动作必须选择目标班级");
            }
            if (GRADUATE.equals(action) && item.targetClassId() != null) {
                throw new BusinessException("毕业动作不能填写目标班级");
            }
            if (values.putIfAbsent(item.sourceClassId(), new Mapping(item.targetClassId(), action)) != null) {
                throw new BusinessException("同一来源班级不能重复配置升级映射");
            }
        }
        return values;
    }

    private Map<Long, EduClassPo> targetClasses(Long schoolId, Long yearId) {
        Map<Long, EduClassPo> values = new HashMap<>();
        for (EduClassPo item : classMapper.selectList(new LambdaQueryWrapper<EduClassPo>()
                .eq(EduClassPo::getSchoolId, schoolId)
                .eq(EduClassPo::getAcademicYearId, yearId)
                .eq(EduClassPo::getNodeType, CLASS)
                .eq(EduClassPo::getStatus, 0))) {
            values.put(item.getId(), item);
        }
        return values;
    }

    private List<EduPersonClassPo> activeMemberships(Long classId, Long academicYearId) {
        List<EduPersonClassPo> all = personClassMapper.selectList(new LambdaQueryWrapper<EduPersonClassPo>()
                .eq(EduPersonClassPo::getClassId, classId)
                .eq(EduPersonClassPo::getMembershipStatus, ACTIVE));
        if (all.stream().anyMatch(item -> item.getAcademicYearId() == null)) {
            throw new BusinessException("来源班级存在未归属学年的人员关系，请完成历史数据校准后再升级");
        }
        return all.stream().filter(item -> Objects.equals(item.getAcademicYearId(), academicYearId)).toList();
    }

    private EduGradePromotionBatchPo requireBatch(Long id) {
        EduGradePromotionBatchPo value = id == null ? null : batchMapper.selectById(id);
        if (value == null) {
            throw new BusinessException("升级批次不存在或不在当前数据范围");
        }
        return value;
    }

    private void requireTeachingSchool(Long id) {
        EduSchoolPo school = id == null ? null : schoolMapper.selectById(id);
        if (!EducationSupport.isOperationalSchool(school)) {
            throw new BusinessException("学校不存在或不在当前数据范围");
        }
        dataScopeService.requireSchool(id);
    }

    private void requireSourceYear(Long id) {
        EduAcademicYearPo year = academicYearMapper.selectById(id);
        if (year == null || AcademicYearStatus.DRAFT.name().equals(year.getStatus())) {
            throw new BusinessException("来源学年不存在或尚未启用");
        }
    }

    private void requireTargetYear(Long id) {
        EduAcademicYearPo year = academicYearMapper.selectById(id);
        if (year == null || AcademicYearStatus.CLOSED.name().equals(year.getStatus())) {
            throw new BusinessException("目标学年不存在或已关闭");
        }
    }

    private static Long requireTenant() {
        Long tenantId = SecurityContextHolder.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("缺少租户上下文");
        }
        return tenantId;
    }

    private static String idempotencyKey(EducationPromotionForms.Preview form, Map<Long, Mapping> mappings) {
        List<String> entries = new ArrayList<>();
        for (Map.Entry<Long, Mapping> entry : mappings.entrySet()) {
            entries.add(entry.getKey() + ":" + entry.getValue().action() + ":" + entry.getValue().targetClassId());
        }
        entries.sort(Comparator.naturalOrder());
        return ClassroomTokenCodec.sha256(form.schoolId() + "|" + form.sourceAcademicYearId() + "|"
                + form.targetAcademicYearId() + "|" + String.join(",", entries));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Mapping(Long targetClassId, String action) {
    }
}
