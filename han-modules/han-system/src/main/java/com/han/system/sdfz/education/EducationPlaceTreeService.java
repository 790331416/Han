package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.common.core.exception.BusinessException;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationPlaceTreeForms;
import com.han.system.sdfz.education.domain.EducationPlaceTreeNode;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
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

/** 建筑—楼层—场所树；存量教室在未回填前仍可作为 PLACE 根节点。 */
@Service
@RequiredArgsConstructor
public class EducationPlaceTreeService {
    private static final String BUILDING = "BUILDING";
    private static final String FLOOR = "FLOOR";
    private static final String PLACE = "PLACE";
    private static final int MAX_BATCH_SIZE = 100;
    private final EduRoomMapper roomMapper;
    private final EduSchoolMapper schoolMapper;
    private final EducationDataScopeService dataScopeService;

    public List<EducationPlaceTreeNode> tree(Long schoolId, Integer status) {
        requireSchool(schoolId);
        List<EduRoomPo> values = roomMapper.selectList(new LambdaQueryWrapper<EduRoomPo>().eq(EduRoomPo::getSchoolId, schoolId)
                .eq(status != null, EduRoomPo::getStatus, status).orderByAsc(EduRoomPo::getNodeLevel).orderByAsc(EduRoomPo::getSort).orderByAsc(EduRoomPo::getRoomName));
        Map<Long, EducationPlaceTreeNode> nodes = new LinkedHashMap<>();
        for (EduRoomPo value : values) nodes.put(value.getId(), EducationPlaceTreeNode.from(value));
        List<EducationPlaceTreeNode> roots = new ArrayList<>();
        for (EducationPlaceTreeNode node : nodes.values()) {
            EducationPlaceTreeNode parent = node.parentId() == null ? null : nodes.get(node.parentId());
            if (parent == null) roots.add(node); else parent.children().add(node);
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long save(EducationPlaceTreeForms.Node form) {
        requireTenant(); requireSchool(form.schoolId());
        EduRoomPo item = form.id() == null ? new EduRoomPo() : requireNode(form.id());
        if (item.getId() != null) requireLocalSource(item.getSourceSystem(), "场所");
        String nodeType = requireType(form.nodeType());
        if (item.getId() != null && !Objects.equals(nodeType, normalize(item.getNodeType()))) {
            throw new BusinessException("节点类型创建后不可修改");
        }
        EduRoomPo parent = form.parentId() == null ? null : requireNode(form.parentId());
        validateParent(item.getId(), parent, form.schoolId(), nodeType);
        TreePath path = pathOf(parent, item.getId());
        String code = item.getId() == null ? EducationCodeGenerator.unique("ROOM", form.roomName(), candidate -> roomMapper.selectCount(
                new LambdaQueryWrapper<EduRoomPo>().eq(EduRoomPo::getSchoolId, form.schoolId()).eq(EduRoomPo::getRoomCode, candidate)) > 0) : item.getRoomCode();
        item.setSchoolId(form.schoolId()); item.setParentId(form.parentId()); item.setAncestors(path.ancestors()); item.setNodeLevel(path.level()); item.setSort(requireSort(form.sort()));
        item.setNodeType(nodeType); item.setRoomCode(code); item.setRoomName(form.roomName().trim());
        item.setAliasName(PLACE.equals(nodeType) ? trimToNull(form.aliasName()) : null);
        item.setRoomType(PLACE.equals(nodeType) ? trimToNull(form.roomType()) : null);
        item.setCapacity(PLACE.equals(nodeType) ? form.capacity() : null); item.setLongitude(form.longitude()); item.setLatitude(form.latitude());
        item.setDataSource(LOCAL_SOURCE); item.setStatus(normalStatus(form.status())); item.setRemark(trimToNull(form.remark()));
        if (item.getId() == null) { item.setTenantId(requireTenant()); item.setSourceSystem(LOCAL_SOURCE); roomMapper.insert(item); }
        else { roomMapper.updateById(item); refreshDescendants(item.getId(), new HashSet<>()); }
        return item.getId();
    }

    /** 按连续序号在建筑下创建楼层。 */
    @Transactional(rollbackFor = Exception.class)
    public int createFloors(EducationPlaceTreeForms.FloorRange form) {
        requireTenant();
        requireSchool(form.schoolId());
        int start = form.startNo();
        int end = form.endNo();
        if (start < 1 || start > end || end - start + 1 > MAX_BATCH_SIZE) {
            throw new BusinessException("批量范围必须从 1 开始递增且一次不超过 " + MAX_BATCH_SIZE + " 条");
        }
        EduRoomPo building = requireNode(form.buildingId());
        if (!Objects.equals(building.getSchoolId(), form.schoolId()) || !BUILDING.equals(normalize(building.getNodeType()))) {
            throw new BusinessException("请先选择当前学校的建筑节点");
        }
        for (int number = start; number <= end; number++) {
            save(new EducationPlaceTreeForms.Node(null, form.schoolId(), form.buildingId(), number + "楼", FLOOR,
                    null, null, null, null, null, number, form.status(), null));
        }
        return end - start + 1;
    }
    private void refreshDescendants(Long parentId, Set<Long> visited) {
        if (!visited.add(parentId)) throw new BusinessException("场所树存在循环引用，请先修复历史数据");
        for (EduRoomPo child : roomMapper.selectList(new LambdaQueryWrapper<EduRoomPo>().eq(EduRoomPo::getParentId, parentId))) {
            TreePath path = pathOf(requireNode(child.getParentId()), child.getId()); child.setAncestors(path.ancestors()); child.setNodeLevel(path.level());
            roomMapper.updateById(child); refreshDescendants(child.getId(), visited);
        }
    }
    private void validateParent(Long selfId, EduRoomPo parent, Long schoolId, String nodeType) {
        if (parent == null) {
            if (selfId == null && !BUILDING.equals(nodeType)) throw new BusinessException("新增根节点只能是建筑");
            return;
        }
        if (Objects.equals(selfId, parent.getId())) throw new BusinessException("场所上级不能是自身");
        if (!Objects.equals(schoolId, parent.getSchoolId())) throw new BusinessException("场所必须挂在同一学校下");
        String parentType = normalize(parent.getNodeType());
        if (PLACE.equals(parentType)) throw new BusinessException("场所是叶子节点，不能继续新增下级");
        if (BUILDING.equals(parentType) && !FLOOR.equals(nodeType) && !PLACE.equals(nodeType)) throw new BusinessException("建筑下只能新增楼层或场所");
        if (FLOOR.equals(parentType) && !PLACE.equals(nodeType)) throw new BusinessException("楼层下只能新增场所");
        if (selfId != null) pathOf(parent, selfId);
    }
    private TreePath pathOf(EduRoomPo parent, Long forbiddenId) {
        if (parent == null) return new TreePath("0", 0);
        List<Long> reversed = new ArrayList<>(); Set<Long> visited = new HashSet<>(); EduRoomPo current = parent;
        while (current != null) { if (!visited.add(current.getId()) || Objects.equals(current.getId(), forbiddenId)) throw new BusinessException("场所上级不能选择自身或其下级节点"); reversed.add(current.getId()); current = current.getParentId() == null ? null : requireNode(current.getParentId()); }
        StringBuilder ancestors = new StringBuilder("0"); for (int i = reversed.size() - 1; i >= 0; i--) ancestors.append(',').append(reversed.get(i)); return new TreePath(ancestors.toString(), reversed.size());
    }
    private EduRoomPo requireNode(Long id) { EduRoomPo value = id == null ? null : roomMapper.selectById(id); if (value == null) throw new BusinessException("场所不存在或不在当前数据范围"); dataScopeService.requireSchool(value.getSchoolId()); return value; }
    private void requireSchool(Long id) { EduSchoolPo value = id == null ? null : schoolMapper.selectById(id); if (value == null || "EDU_BUREAU".equals(value.getOrgType())) throw new BusinessException("学校不存在或不在当前数据范围"); dataScopeService.requireSchool(id); }
    private static String requireType(String value) { String type = normalize(value); if (!BUILDING.equals(type) && !FLOOR.equals(type) && !PLACE.equals(type)) throw new BusinessException("节点类型只能是 BUILDING、FLOOR 或 PLACE"); return type; }
    private static int requireSort(Integer value) { if (value == null || value < 0) throw new BusinessException("排序值必须是不小于 0 的整数"); return value; }
    private static String normalize(String value) { return value == null ? "" : value.trim().toUpperCase(Locale.ROOT); }
    private record TreePath(String ancestors, int level) { }
}
