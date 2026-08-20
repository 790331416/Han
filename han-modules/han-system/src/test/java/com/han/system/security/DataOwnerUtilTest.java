package com.han.system.security;

import com.han.common.core.exception.ForbiddenException;
import com.han.common.security.context.SecurityContextHolder;
import com.han.common.security.domain.LoginUser;
import com.han.common.security.util.DataOwnerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataOwnerUtilTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clear();
    }

    @Test
    void adminRoleCanAssignEducationRole() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2089763675040444417L)
                .tenantId(1L)
                .roleKeys(Set.of("admin"))
                .build());

        assertThatCode(() -> DataOwnerUtil.checkRolePermission(Set.of(202608120101L)))
                .doesNotThrowAnyException();
    }

    @Test
    void ordinaryUserCannotAssignRoleTheyDoNotOwn() {
        SecurityContextHolder.setLoginUser(LoginUser.builder()
                .userId(2L)
                .tenantId(1L)
                .roleKeys(Set.of("user"))
                .roleIds(Set.of(4L))
                .build());

        assertThatThrownBy(() -> DataOwnerUtil.checkRolePermission(Set.of(202608120101L)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("202608120101");
    }
}
