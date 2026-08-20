package com.han.system.sdfz.education;

import com.han.common.core.exception.BusinessException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EducationPlaceTreeForms;
import com.han.system.sdfz.education.domain.EducationPlaceTreeNode;
import com.han.system.sdfz.education.mapper.EduRoomMapper;
import com.han.system.sdfz.education.mapper.EduSchoolMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationPlaceTreeServiceTest {
    @Mock private EduRoomMapper roomMapper;
    @Mock private EduSchoolMapper schoolMapper;
    @Mock private EducationDataScopeService dataScopeService;
    private EducationPlaceTreeService service;

    @BeforeEach void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(2L).tenantId(1L).build());
        service = new EducationPlaceTreeService(roomMapper, schoolMapper, dataScopeService);
        EduSchoolPo school = new EduSchoolPo(); school.setId(7L); school.setOrgType("SCHOOL");
        when(schoolMapper.selectById(7L)).thenReturn(school);
    }

    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void createsPlaceBelowFloor() {
        when(roomMapper.selectById(10L)).thenReturn(node(10L, null, "BUILDING"));
        when(roomMapper.selectById(11L)).thenReturn(node(11L, 10L, "FLOOR"));
        when(roomMapper.selectCount(any())).thenReturn(0L);
        doAnswer(call -> { ((EduRoomPo) call.getArgument(0)).setId(20L); return 1; }).when(roomMapper).insert(any(EduRoomPo.class));

        Long id = service.save(form(null, 11L, "101 教室", "PLACE"));

        ArgumentCaptor<EduRoomPo> captor = ArgumentCaptor.forClass(EduRoomPo.class);
        org.mockito.Mockito.verify(roomMapper).insert(captor.capture());
        assertThat(id).isEqualTo(20L);
        assertThat(captor.getValue()).extracting(EduRoomPo::getParentId, EduRoomPo::getNodeType, EduRoomPo::getNodeLevel)
                .containsExactly(11L, "PLACE", 2);
    }

    @Test
    void rejectsChildBelowPlaceLeaf() {
        when(roomMapper.selectById(10L)).thenReturn(node(10L, null, "PLACE"));

        assertThatThrownBy(() -> service.save(form(null, 10L, "不应存在的下级", "PLACE")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("叶子节点");
    }

    @Test
    void buildsTreeFromParentRelation() {
        EduRoomPo building = node(10L, null, "BUILDING");
        EduRoomPo floor = node(20L, 10L, "FLOOR");
        EduRoomPo place = node(30L, 20L, "PLACE");
        when(roomMapper.selectList(any())).thenReturn(List.of(building, floor, place));

        List<EducationPlaceTreeNode> nodes = service.tree(7L, 0);

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().children().getFirst().children()).extracting(EducationPlaceTreeNode::id).containsExactly(30L);
    }

    @Test
    void batchCreatesFloorsWithNumberAsSort() {
        when(roomMapper.selectById(10L)).thenReturn(node(10L, null, "BUILDING"));
        when(roomMapper.selectCount(any())).thenReturn(0L);
        doAnswer(call -> { ((EduRoomPo) call.getArgument(0)).setId(20L); return 1; }).when(roomMapper).insert(any(EduRoomPo.class));

        int created = service.createFloors(new EducationPlaceTreeForms.FloorRange(7L, 10L, 1, 2, 0));

        ArgumentCaptor<EduRoomPo> captor = ArgumentCaptor.forClass(EduRoomPo.class);
        org.mockito.Mockito.verify(roomMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(created).isEqualTo(2);
        assertThat(captor.getAllValues()).extracting(EduRoomPo::getRoomName, EduRoomPo::getSort)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("1楼", 1), org.assertj.core.groups.Tuple.tuple("2楼", 2));
    }

    private static EducationPlaceTreeForms.Node form(Long id, Long parentId, String name, String type) {
        return new EducationPlaceTreeForms.Node(id, 7L, parentId, name, type, null, "普通教室", 40, null, null, 0, 0, null);
    }

    private static EduRoomPo node(Long id, Long parentId, String type) {
        EduRoomPo value = new EduRoomPo();
        value.setId(id); value.setSchoolId(7L); value.setParentId(parentId); value.setNodeType(type); value.setRoomName("节点" + id);
        value.setRoomCode("R" + id); value.setSourceSystem("HAN"); value.setStatus(0); return value;
    }
}
