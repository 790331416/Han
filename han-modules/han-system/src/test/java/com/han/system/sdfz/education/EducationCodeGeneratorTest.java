package com.han.system.sdfz.education;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EducationCodeGeneratorTest {

    @Test
    void createsReadableNameCodesAndAddsOnlyTheNeededSuffix() {
        assertEquals("SCHOOL_BA_SHU_YUN_XIAO", EducationCodeGenerator.code("SCHOOL", "巴蜀云校"));
        assertEquals("GRADE_GAO_YI_NIAN_JI", EducationCodeGenerator.gradeCode("高一年级2班"));

        Set<String> existing = new HashSet<>(Set.of("PERSON_ZHANG_SAN"));
        String generated = EducationCodeGenerator.unique("PERSON", "张三", existing::contains);
        assertEquals("PERSON_ZHANG_SAN_2", generated);
    }
}
