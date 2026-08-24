package com.han.system.sdfz.education;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.han.system.domain.po.SysUserPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.mapper.EduPersonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;

/** 已绑定教育人员的账号资料同步入口，避免姓名和手机号在两张表长期分叉。 */
@Service
@RequiredArgsConstructor
public class EducationAccountIdentityService {

    private final EduPersonMapper personMapper;

    /** 个人中心、系统用户编辑等账号入口修改后，同步到关联教育人员。 */
    public void syncFromAccount(SysUserPo account) {
        if (account == null || account.getId() == null) return;
        sync(account.getId(), text(account.getNickname()), text(account.getPhone()));
    }

    /** 教育人员入口修改后，同步同一账号关联的全部教育身份。 */
    public void syncFromPerson(Long userId, String personName, String phone) {
        if (userId == null) return;
        sync(userId, text(personName), text(phone));
    }

    private void sync(Long userId, String personName, String phone) {
        if (personName == null && phone == null) return;
        for (EduPersonPo person : personMapper.selectList(new LambdaQueryWrapper<EduPersonPo>()
                .eq(EduPersonPo::getUserId, userId))) {
            boolean changed = false;
            if (personName != null && !Objects.equals(personName, person.getPersonName())) {
                person.setPersonName(personName);
                changed = true;
            }
            if (phone != null && !Objects.equals(phone, person.getPhone())) {
                person.setPhone(phone);
                changed = true;
            }
            if (changed) personMapper.updateById(person);
        }
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
