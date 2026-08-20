package com.han.system.sdfz.education;

import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationPromotionServiceTest {
    @Mock private EduAcademicYearMapper academicYearMapper;
    @Mock private EduSchoolMapper schoolMapper;
    @Mock private EduClassMapper classMapper;
    @Mock private EduPersonMapper personMapper;
    @Mock private EduPersonClassMapper personClassMapper;
    @Mock private EduGradePromotionBatchMapper batchMapper;
    @Mock private EduGradePromotionItemMapper itemMapper;
    @Mock private EducationDataScopeService dataScopeService;
    private EducationPromotionService service;

    @BeforeAll static void bootstrapEntityMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        List.of(EduAcademicYearPo.class, EduSchoolPo.class, EduClassPo.class, EduPersonPo.class,
                EduPersonClassPo.class, EduGradePromotionBatchPo.class, EduGradePromotionItemPo.class)
                .forEach(entity -> TableInfoHelper.initTableInfo(assistant, entity));
    }

    @BeforeEach void setUp() {
        SecurityContextHolder.setLoginUser(LoginUser.builder().userId(1L).tenantId(1L).build());
        service = new EducationPromotionService(academicYearMapper, schoolMapper, classMapper, personMapper,
                personClassMapper, batchMapper, itemMapper, dataScopeService);
    }
    @AfterEach void tearDown() { SecurityContextHolder.clear(); }

    @Test
    void previewOnlyCreatesAuditableItemsAndDoesNotChangeMemberships() {
        when(schoolMapper.selectById(7L)).thenReturn(school());
        when(academicYearMapper.selectById(1L)).thenReturn(year("ACTIVE"));
        when(academicYearMapper.selectById(2L)).thenReturn(year("DRAFT"));
        when(batchMapper.selectOne(any())).thenReturn(null);
        when(classMapper.selectList(any())).thenReturn(List.of(sourceClass()), List.of(targetClass()));
        when(personClassMapper.selectList(any())).thenReturn(List.of(sourceMembership()));
        when(personMapper.selectById(100L)).thenReturn(student());
        doAnswer(invocation -> { ((EduGradePromotionBatchPo) invocation.getArgument(0)).setId(900L); return 1; })
                .when(batchMapper).insert(any(EduGradePromotionBatchPo.class));

        EduGradePromotionBatchPo batch = service.preview(new EducationPromotionForms.Preview(7L, 1L, 2L,
                List.of(new EducationPromotionForms.ClassMapping(11L, 21L, "PROMOTE")), "2026 升级预览"));

        ArgumentCaptor<EduGradePromotionItemPo> item = ArgumentCaptor.forClass(EduGradePromotionItemPo.class);
        verify(itemMapper).insert(item.capture());
        assertThat(batch.getStatus()).isEqualTo("DRAFT");
        assertThat(batch.getTotalCount()).isEqualTo(1);
        assertThat(item.getValue()).extracting(EduGradePromotionItemPo::getPersonId,
                EduGradePromotionItemPo::getSourceClassId, EduGradePromotionItemPo::getTargetClassId,
                EduGradePromotionItemPo::getResultStatus).containsExactly(100L, 11L, 21L, "PENDING");
    }

    @Test
    void confirmCopiesTargetMembershipAndKeepsSourceAsCompletedHistory() {
        EduGradePromotionBatchPo batch = new EduGradePromotionBatchPo();
        batch.setId(900L); batch.setSchoolId(7L); batch.setSourceAcademicYearId(1L); batch.setTargetAcademicYearId(2L); batch.setStatus("DRAFT");
        EduGradePromotionItemPo item = new EduGradePromotionItemPo();
        item.setId(901L); item.setPersonId(100L); item.setSourceClassId(11L); item.setTargetClassId(21L); item.setAction("PROMOTE"); item.setResultStatus("PENDING");
        EduPersonClassPo source = sourceMembership();
        when(batchMapper.selectById(900L)).thenReturn(batch);
        when(schoolMapper.selectById(7L)).thenReturn(school());
        when(batchMapper.update(any(), any())).thenReturn(1);
        when(itemMapper.selectList(any())).thenReturn(List.of(item));
        when(personClassMapper.selectOne(any())).thenReturn(source);
        when(personClassMapper.selectCount(any())).thenReturn(0L);

        EduGradePromotionBatchPo result = service.confirm(900L);

        ArgumentCaptor<EduPersonClassPo> target = ArgumentCaptor.forClass(EduPersonClassPo.class);
        verify(personClassMapper).insert(target.capture());
        assertThat(target.getValue()).extracting(EduPersonClassPo::getPersonId, EduPersonClassPo::getClassId,
                EduPersonClassPo::getAcademicYearId, EduPersonClassPo::getMembershipStatus)
                .containsExactly(100L, 21L, 2L, "ACTIVE");
        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(source.getMembershipStatus()).isEqualTo("COMPLETED");
    }

    private static EduSchoolPo school() { EduSchoolPo value = new EduSchoolPo(); value.setId(7L); value.setOrgType("SCHOOL"); value.setSchoolManageType("INDEPENDENT"); return value; }
    private static EduAcademicYearPo year(String status) { EduAcademicYearPo value = new EduAcademicYearPo(); value.setStatus(status); return value; }
    private static EduClassPo sourceClass() { EduClassPo value = new EduClassPo(); value.setId(11L); value.setSchoolId(7L); value.setAcademicYearId(1L); value.setNodeType("CLASS"); value.setStatus(0); return value; }
    private static EduClassPo targetClass() { EduClassPo value = new EduClassPo(); value.setId(21L); value.setSchoolId(7L); value.setAcademicYearId(2L); value.setNodeType("CLASS"); value.setStatus(0); return value; }
    private static EduPersonPo student() { EduPersonPo value = new EduPersonPo(); value.setId(100L); value.setPersonType("STUDENT"); return value; }
    private static EduPersonClassPo sourceMembership() { EduPersonClassPo value = new EduPersonClassPo(); value.setId(301L); value.setTenantId(1L); value.setPersonId(100L); value.setClassId(11L); value.setAcademicYearId(1L); value.setMembershipRole("STUDENT"); value.setMembershipStatus("ACTIVE"); return value; }
}
