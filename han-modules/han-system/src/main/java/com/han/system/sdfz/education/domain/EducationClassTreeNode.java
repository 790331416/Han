package com.han.system.sdfz.education.domain;

import java.util.ArrayList;
import java.util.List;

/** 班级树展示节点；只有 CLASS 节点可被人员、订单选择。 */
public record EducationClassTreeNode(
        Long id, Long schoolId, Long parentId, Long academicYearId,
        String classCode, String className, String gradeCode, String branchCode,
        String nodeType, Integer cohortYear, Integer nodeLevel, Integer sort, Integer status,
        List<EducationClassTreeNode> children) {
    public static EducationClassTreeNode from(EduClassPo item) {
        return new EducationClassTreeNode(item.getId(), item.getSchoolId(), item.getParentId(), item.getAcademicYearId(),
                item.getClassCode(), item.getClassName(), item.getGradeCode(), item.getBranchCode(), item.getNodeType(),
                item.getCohortYear(), item.getNodeLevel(), item.getSort(), item.getStatus(), new ArrayList<>());
    }
}
