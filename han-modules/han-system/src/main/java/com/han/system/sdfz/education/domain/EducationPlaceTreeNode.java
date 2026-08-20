package com.han.system.sdfz.education.domain;

import java.util.ArrayList;
import java.util.List;

/** 场所树展示节点；仅 PLACE 节点允许设备挂载。 */
public record EducationPlaceTreeNode(
        Long id, Long schoolId, Long parentId, String roomCode, String roomName,
        String aliasName, String roomType, String nodeType, Integer nodeLevel, Integer sort,
        Integer capacity, Integer status, List<EducationPlaceTreeNode> children) {
    public static EducationPlaceTreeNode from(EduRoomPo item) {
        return new EducationPlaceTreeNode(item.getId(), item.getSchoolId(), item.getParentId(), item.getRoomCode(),
                item.getRoomName(), item.getAliasName(), item.getRoomType(), item.getNodeType(), item.getNodeLevel(), item.getSort(),
                item.getCapacity(), item.getStatus(), new ArrayList<>());
    }
}
