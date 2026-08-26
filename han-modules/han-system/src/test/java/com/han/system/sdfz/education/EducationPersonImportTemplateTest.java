package com.han.system.sdfz.education;

import com.alibaba.excel.EasyExcel;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysRolePo;
import com.han.system.mapper.SysDictDataMapper;
import com.han.system.mapper.SysRoleMapper;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import com.han.system.sdfz.education.domain.EduSubjectPo;
import com.han.system.sdfz.education.domain.EducationForms;
import com.han.system.sdfz.education.domain.EducationPersonImportVo;
import com.han.system.sdfz.education.mapper.EduClassMapper;
import com.han.system.sdfz.education.mapper.EduSubjectMapper;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EducationPersonImportTemplateTest {

    @Mock private EducationMasterDataService masterDataService;
    @Mock private EducationPersonService personService;
    @Mock private EduClassMapper classMapper;
    @Mock private EduSubjectMapper subjectMapper;
    @Mock private SysDictDataMapper dictDataMapper;
    @Mock private SysRoleMapper roleMapper;

    @Test
    void templateSeparatesPersonTypesAndFiltersClassesByGrade() throws Exception {
        EduSchoolPo school = new EduSchoolPo();
        school.setSchoolName("测试学校");
        when(personService.requireImportSchool(11L)).thenReturn(school);

        EduClassPo grade7 = node(101L, null, "GRADE", "七年级");
        EduClassPo class71 = node(102L, 101L, "CLASS", "1班");
        EduClassPo grade10 = node(201L, null, "GRADE", "高一年级");
        EduClassPo class101 = node(202L, 201L, "CLASS", "1班");
        EduClassPo class104 = node(203L, 201L, "CLASS", "4班");
        when(classMapper.selectList(any())).thenReturn(List.of(grade7, class71, grade10, class101, class104));

        EduSubjectPo subject = new EduSubjectPo();
        subject.setId(301L);
        subject.setSubjectName("语文");
        when(subjectMapper.selectList(any())).thenReturn(List.of(subject));
        SysDictDataPo duty = new SysDictDataPo();
        duty.setDictLabel("普通教师");
        when(dictDataMapper.selectList(any())).thenReturn(List.of(duty));
        when(roleMapper.selectList(any())).thenReturn(List.<SysRolePo>of());

        EducationMasterDataController controller = controller();
        MockHttpServletResponse response = new MockHttpServletResponse();
        controller.personImportTemplate(11L, response);
        byte[] template = response.getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(template))) {
            Sheet people = workbook.getSheet("人员导入");
            assertThat(people.getRow(0).getCell(2).getStringCellValue()).isEqualTo("人员类型（必填）");
            assertThat(people.getRow(0)).extracting(cell -> cell.getStringCellValue())
                    .doesNotContain("清除管理端角色", "归班角色");
            assertThat(people.getRow(1).getCell(12).getStringCellValue()).isEqualTo("七年级");
            assertThat(people.getRow(1).getCell(13).getStringCellValue()).isEqualTo("七年级/1班");

            Sheet options = workbook.getSheet("下拉选项");
            assertThat(workbook.getSheetVisibility(workbook.getSheetIndex(options))).isEqualTo(SheetVisibility.HIDDEN);
            assertThat(options.getRow(1).getCell(2).getStringCellValue()).isEqualTo("教师");
            assertThat(options.getRow(2).getCell(2).getStringCellValue()).isEqualTo("学生");
            assertThat(workbook.getName("person_import_options_2").getRefersToFormula())
                    .isEqualTo("'下拉选项'!$C$2:$C$3");
            assertThat(workbook.getName("person_import_grade_class_map").getRefersToFormula())
                    .isEqualTo("'下拉选项'!$Q$2:$R$3");
            assertThat(workbook.getName("person_import_grade_0").getRefersToFormula())
                    .isEqualTo("'下拉选项'!$S$2:$S$2");
            assertThat(workbook.getName("person_import_grade_1").getRefersToFormula())
                    .isEqualTo("'下拉选项'!$T$2:$T$3");
            assertThat(options.getRow(1).getCell(18).getStringCellValue()).isEqualTo("七年级/1班");
            assertThat(options.getRow(1).getCell(19).getStringCellValue()).isEqualTo("高一年级/1班");
            assertThat(options.getRow(2).getCell(19).getStringCellValue()).isEqualTo("高一年级/4班");
            assertThat(people.getDataValidations().stream().map(DataValidation::getValidationConstraint)
                    .map(constraint -> constraint.getFormula1()).filter(value -> value != null && value.contains("VLOOKUP")))
                    .containsExactly("INDIRECT(VLOOKUP($M4,person_import_grade_class_map,2,FALSE))");
        }

        List<EducationPersonImportVo> imported = EasyExcel.read(new ByteArrayInputStream(template))
                .head(EducationPersonImportVo.class).sheet("人员导入").doReadSync();
        assertThat(imported).hasSize(2);
        assertThat(imported.get(0)).extracting(EducationPersonImportVo::getPersonType,
                        EducationPersonImportVo::getGradeNames, EducationPersonImportVo::getClassNames)
                .containsExactly("教师", "七年级", "七年级/1班");

        EducationPersonImportVo student = new EducationPersonImportVo();
        student.setPersonName("李同学");
        student.setPersonType("学生");
        student.setPhone("13900000000");
        student.setStatus("正常");
        student.setLoginEnabled("否");
        student.setGradeNames("高一年级");
        student.setClassNames("高一年级/4班");
        Method converter = EducationMasterDataController.class.getDeclaredMethod(
                "toPersonForm", EducationPersonImportVo.class, Long.class);
        converter.setAccessible(true);
        EducationForms.Person form = (EducationForms.Person) converter.invoke(controller, student, 11L);
        assertThat(form.classIds()).containsExactly(203L);
    }

    private EducationMasterDataController controller() {
        return new EducationMasterDataController(masterDataService, personService, classMapper, subjectMapper,
                dictDataMapper, roleMapper);
    }

    private static EduClassPo node(Long id, Long parentId, String type, String name) {
        EduClassPo value = new EduClassPo();
        value.setId(id);
        value.setParentId(parentId);
        value.setNodeType(type);
        value.setClassName(name);
        return value;
    }
}
