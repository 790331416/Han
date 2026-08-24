package com.han.system.sdfz.education;

import com.han.system.domain.po.SysUserPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EducationAccountIdentityServiceTest {

    @Test
    void accountChangeUpdatesEveryLinkedEducationIdentity() {
        EduPersonMapper personMapper = mock(EduPersonMapper.class);
        EducationAccountIdentityService service = new EducationAccountIdentityService(personMapper);
        EduPersonPo first = person(11L, "旧姓名", "13800000000");
        EduPersonPo second = person(12L, "旧姓名", "13800000000");
        when(personMapper.selectList(any())).thenReturn(List.of(first, second));
        SysUserPo account = new SysUserPo();
        account.setId(1L);
        account.setNickname("新姓名");
        account.setPhone("13900000000");

        service.syncFromAccount(account);

        assertThat(first).extracting(EduPersonPo::getPersonName, EduPersonPo::getPhone)
                .containsExactly("新姓名", "13900000000");
        assertThat(second).extracting(EduPersonPo::getPersonName, EduPersonPo::getPhone)
                .containsExactly("新姓名", "13900000000");
        verify(personMapper).updateById(first);
        verify(personMapper).updateById(second);
    }

    private static EduPersonPo person(Long id, String name, String phone) {
        EduPersonPo value = new EduPersonPo();
        value.setId(id);
        value.setPersonName(name);
        value.setPhone(phone);
        return value;
    }
}
