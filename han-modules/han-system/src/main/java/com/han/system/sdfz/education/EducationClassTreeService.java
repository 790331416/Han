package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduAcademicYearPo;
import com.han.system.sdfz.education.domain.EducationClassTreeForms;
import com.han.system.sdfz.education.domain.EducationClassTreeNode;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import com.han.system.sdfz.education.mapper.EduAcademicYearMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.han.system.sdfz.education.EducationSupport.LOCAL_SOURCE;
import static com.han.system.sdfz.education.EducationSupport.normalStatus;
import static com.han.system.sdfz.education.EducationSupport.requireLocalSource;
import static com.han.system.sdfz.education.EducationSupport.requireTenant;
import static com.han.system.sdfz.education.EducationSupport.trimToNull;

/** 学年内的年级、专业和班级树；存量扁平班级仍视为 CLASS 根节点。 */
@Service
@RequiredArgsConstructor
public class EducationClassTreeService {
    private static final String GRADE = "GRADE";
    private static final String MAJOR = "MAJOR";
    private static final String CLASS = "CLASS";
    private static final int MAX_BATCH_SIZE = 100;
    /** 小学一年级到高中三年级的标准年级编码；幼儿园、学前班等仍通过单条维护。 */
    private static final Map<Integer, GradePreset> STANDARD_GRADES = Map.ofEntries(
            Map.entry(1, new GradePreset("一年级", "G004")),
            Map.entry(2, new GradePreset("二年级", "G005")),
            Map.entry(3, new GradePreset("三年级", "G006")),
            Map.entry(4, new GradePreset("四年级", "G007")),
            Map.entry(5, new GradePreset("五年级", "G008")),
            Map.entry(6, new GradePreset("六年级", "G009")),
            Map.entry(7, new GradePreset("七年级", "G010")),
            Map.entry(8, new GradePreset("八年级", "G011")),
            Map.entry(9, new GradePreset("九年级", "G012")),
            Map.entry(10, new GradePreset("高一年级", "G013")),
            Map.entry(11, new GradePreset("高二年级", "G014")),
            Map.entry(12, new GradePreset("高三年级", "G015")));

    private final EduClassMapper classMapper;
    private final EduSchoolMapper schoolMapper;
    private final EduAcademicYearMapper academicYearMapper;
    private final EducationDataScopeService dataScopeService;

    public List<EducationClassTreeNode> tree(Long schoolId, Long academicYearId, Integer status) {
        requireTeachingSchool(schoolId);
        List<EduClassPo> values = classMapper.selectList(new LambdaQueryWrapper<EduClassPo>()
                .eq(EduClassPo::getSchoolId, schoolId)
                .eq(academicYearId != null, EduClassPo::getAcademicYearId, academicYearId)
                .eq(status != null, EduClassPo::getStatus, status)
                .orderByAsc(EduClassPo::getNodeLevel).orderByAsc(EduClassPo::getSort).orderByAsc(EduClassPo::getClassName));
        Map<Long, EducationClassTreeNode> nodes = new LinkedHashMap<>();
        for (EduClassPo value : values) nodes.put(value.getId(), EducationClassTreeNode.from(value));
        List<EducationClassTreeNode> roots = new ArrayList<>();
        for (EducationClassTreeNode node : nodes.values()) {
            EducationClassTreeNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(EducationClassTreeForms.Node form) {
        requireTenant();
        requireTeachingSchool(form.schoolId());
        requireAcademicYear(form.academicYearId(), form.schoolId());
        EduClassPo item = form.id() == null ? new EduClassPo() : requireNode(form.id());
        if (item.getId() != null) requireLocalSource(item.getSourceSystem(), "教学组织");
        String nodeType = requireNodeType(form.nodeType());
        if (item.getId() != null && !Objects.equals(nodeType, normalize(item.getNodeType()))) {
            throw new BusinessException("节点类型创建后不可修改");
        }
        EduClassPo parent = form.parentId() == null ? null : requireNode(form.parentId());
        validateParent(item.getId(), parent, form.schoolId(), form.academicYearId(), nodeType);
        String branchCode = GRADE.equals(nodeType) ? requireBranchCode(form.branchCode()) : trimToNull(form.branchCode());
        TreePath path = pathOf(parent, item.getId());
        String code = item.getId() == null ? EducationCodeGenerator.unique("CLASS", form.className(), candidate -> classMapper.selectCount(
                new LambdaQueryWrapper<EduClassPo>().eq(EduClassPo::getSchoolId, form.schoolId()).eq(EduClassPo::getClassCode, candidate)) > 0) : item.getClassCode();
        item.setSchoolId(form.schoolId());
        item.setParentId(form.parentId());
        item.setAncestors(path.ancestors());
        item.setNodeLevel(path.level());
        item.setSort(requireSort(form.sort()));
        item.setAcademicYearId(form.academicYearId());
        item.setNodeType(nodeType);
        item.setBranchCode(branchCode);
        item.setGradeCode(GRADE.equals(nodeType) ? branchCode : parent == null ? null : parent.getGradeCode());
        item.setCohortYear(GRADE.equals(nodeType) ? requireCohortYear(form.cohortYear())
                : parent == null ? item.getCohortYear() : requireInheritedCohort(parent));
        item.setClassCode(code);
        item.setClassName(form.className().trim());
        item.setClassRole(trimToNull(form.classRole()) == null ? "NORMAL" : form.classRole().trim());
        item.setStatus(normalStatus(form.status()));
        item.setRemark(trimToNull(form.remark()));
        if (item.getId() == null) {
            item.setTenantId(requireTenant()); item.setSourceSystem(LOCAL_SOURCE); classMapper.insert(item);
        } else {
            classMapper.updateById(item); refreshDescendants(item.getId(), new HashSet<>());
        }
        return item.getId();
    }

    /** 按连续序号创建标准年级或指定年级/专业下的班级。 */
    @Transactional(rollbackFor = Exception.class)
    public int createRange(EducationClassTreeForms.Range form) {
        requireTenant();
        requireTeachingSchool(form.schoolId());
        requireAcademicYear(form.academicYearId(), form.schoolId());
        int start = form.startNo();
        int end = form.endNo();
        requireRange(start, end);
        String type = requireNodeType(form.nodeType());
        if (!GRADE.equals(type) && !CLASS.equals(type)) {
            throw new BusinessException("批量创建只支持年级或班级");
        }
        if (GRADE.equals(type) && form.parentId() != null) {
            throw new BusinessException("批量年级必须直接创建在学校学年根节点下");
        }
        if (GRADE.equals(type)) requireCohortYear(form.cohortYear());
        if (CLASS.equals(type) && form.parentId() == null) {
            throw new BusinessException("批量班级必须先选择年级或专业分组");
        }
        int created = 0;
        for (int number = start; number <= end; number++) {
            if (GRADE.equals(type)) {
                GradePreset preset = STANDARD_GRADES.get(number);
                if (preset == null) {
                    throw new BusinessException("批量年级仅支持 1 至 12，对应一年级至高三年级");
                }
                save(new EducationClassTreeForms.Node(null, form.schoolId(), null, form.academicYearId(),
                        preset.name(), GRADE, preset.branchCode(), form.cohortYear(), "NORMAL", number, form.status(), null));
            } else {
                save(new EducationClassTreeForms.Node(null, form.schoolId(), form.parentId(), form.academicYearId(),
                        number + "班", CLASS, null, null, "NORMAL", number, form.status(), null));
            }
            created++;
        }
        return created;
    }

    private void refreshDescendants(Long parentId, Set<Long> visited) {
        if (!visited.add(parentId)) throw new BusinessException("教学组织存在循环引用，请先修复历史数据");
        for (EduClassPo child : classMapper.selectList(new LambdaQueryWrapper<EduClassPo>().eq(EduClassPo::getParentId, parentId))) {
            TreePath path = pathOf(requireNode(child.getParentId()), child.getId());
            child.setAncestors(path.ancestors()); child.setNodeLevel(path.level());
            child.setGradeCode(GRADE.equals(child.getNodeType()) ? child.getBranchCode() : requireNode(child.getParentId()).getGradeCode());
            child.setCohortYear(GRADE.equals(child.getNodeType()) ? child.getCohortYear() : requireInheritedCohort(requireNode(child.getParentId())));
            classMapper.updateById(child); refreshDescendants(child.getId(), visited);
        }
    }

    private void validateParent(Long selfId, EduClassPo parent, Long schoolId, Long academicYearId, String nodeType) {
        if (parent == null) {
            if (selfId == null && !GRADE.equals(nodeType)) throw new BusinessException("新增根节点只能是年级");
            return;
        }
        if (Objects.equals(selfId, parent.getId())) throw new BusinessException("教学组织上级不能是自身");
        if (!Objects.equals(schoolId, parent.getSchoolId()) || !Objects.equals(academicYearId, parent.getAcademicYearId()))
            throw new BusinessException("教学组织必须挂在同一学校、同一学年下");
        if (CLASS.equals(normalize(parent.getNodeType()))) throw new BusinessException("班级是叶子节点，不能继续新增下级");
        if (GRADE.equals(nodeType)) throw new BusinessException("年级只能作为学校学年的根节点");
        if (selfId != null) pathOf(parent, selfId);
    }

    private TreePath pathOf(EduClassPo parent, Long forbiddenId) {
        if (parent == null) return new TreePath("0", 0);
        List<Long> reversed = new ArrayList<>(); Set<Long> visited = new HashSet<>(); EduClassPo current = parent;
        while (current != null) {
            if (!visited.add(current.getId()) || Objects.equals(current.getId(), forbiddenId)) throw new BusinessException("教学组织上级不能选择自身或其下级节点");
            reversed.add(current.getId()); current = current.getParentId() == null ? null : requireNode(current.getParentId());
        }
        StringBuilder ancestors = new StringBuilder("0");
        for (int index = reversed.size() - 1; index >= 0; index--) ancestors.append(',').append(reversed.get(index));
        return new TreePath(ancestors.toString(), reversed.size());
    }

    private EduClassPo requireNode(Long id) {
        EduClassPo value = id == null ? null : classMapper.selectById(id);
        if (value == null) throw new BusinessException("教学组织不存在或不在当前数据范围");
        dataScopeService.requireSchool(value.getSchoolId());
        return value;
    }
    private void requireTeachingSchool(Long id) {
        EduSchoolPo value = id == null ? null : schoolMapper.selectById(id);
        if (!EducationSupport.isOperationalSchool(value)) throw new BusinessException("学校不存在或不在当前数据范围");
        dataScopeService.requireSchool(id);
    }
    private void requireAcademicYear(Long academicYearId, Long schoolId) {
        EduAcademicYearPo year = academicYearId == null ? null : academicYearMapper.selectById(academicYearId);
        if (year == null || !Objects.equals(year.getSchoolId(), schoolId)) {
            throw new BusinessException("所选学年不存在或不属于当前学校");
        }
    }
    private static String requireNodeType(String value) {
        String type = normalize(value);
        if (!GRADE.equals(type) && !MAJOR.equals(type) && !CLASS.equals(type)) throw new BusinessException("节点类型只能是 GRADE、MAJOR 或 CLASS");
        return type;
    }
    private static String requireBranchCode(String value) {
        String code = trimToNull(value);
        if (code == null) throw new BusinessException("年级节点必须选择年级编码");
        return code;
    }
    private static int requireCohortYear(Integer value) {
        if (value == null || value < 1900 || value > 2100) throw new BusinessException("入学届别应选择 1900 至 2100 年");
        return value;
    }
    private static int requireSort(Integer value) {
        if (value == null || value < 0) throw new BusinessException("排序值必须是不小于 0 的整数");
        return value;
    }
    private static void requireRange(int start, int end) {
        if (start < 1 || start > end || end - start + 1 > MAX_BATCH_SIZE) {
            throw new BusinessException("批量范围必须从 1 开始递增且一次不超过 " + MAX_BATCH_SIZE + " 条");
        }
    }
    private static Integer requireInheritedCohort(EduClassPo parent) {
        if (parent.getCohortYear() == null) throw new BusinessException("上级年级缺少入学届别，请先补齐后再维护下级班级");
        return parent.getCohortYear();
    }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private record GradePreset(String name, String branchCode) { }
    private record TreePath(String ancestors, int level) { }
}
