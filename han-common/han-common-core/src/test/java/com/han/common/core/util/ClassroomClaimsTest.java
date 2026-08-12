package com.han.common.core.util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClassroomClaimsTest {

    @Test
    void pinsUserTypeToThePortalFilterConstantAndKeepsRoleTypeSeparate() {
        Map<String, Object> claims = ClassroomClaims.build(
                "100", "Teacher One", "2", List.of("2"), "identity-1", "school-1", "100");

        assertThat(claims)
                .containsEntry("userType", "USER")
                .containsEntry("roleType", "2")
                .containsEntry("status", 0);
    }

    @Test
    void serializesUserIdAsJsonStringForTheLegacyUserDto() {
        String json = HanJsonUtil.toJsonString(ClassroomClaims.build(
                "1234567890123456789", "Teacher", "2", List.of("2"), "id", "school", "1"));

        assertThat(json).contains("\"userId\":\"1234567890123456789\"");
    }

    @Test
    void replacesMissingValuesWithEmptyStringsSoPayloadNeverCarriesNull() {
        Map<String, Object> claims = ClassroomClaims.build(
                null, null, null, null, null, null, null);

        assertThat(claims.values()).doesNotContainNull();
        assertThat(claims).containsEntry("roles", List.of());
    }

    @Test
    void normalizesRolesByTrimmingDroppingBlanksAndKeepingFirstOccurrence() {
        assertThat(ClassroomClaims.normalizeRoles(Arrays.asList(" 2 ", "", null, "teacher", "2")))
                .containsExactly("2", "teacher");
    }
}
