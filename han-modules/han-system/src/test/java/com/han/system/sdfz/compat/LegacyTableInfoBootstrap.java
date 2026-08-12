package com.han.system.sdfz.compat;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.han.system.domain.po.SysDictDataPo;
import com.han.system.domain.po.SysUserPo;
import com.han.system.sdfz.education.domain.EduClassPo;
import com.han.system.sdfz.education.domain.EduDevicePo;
import com.han.system.sdfz.education.domain.EduPersonClassPo;
import com.han.system.sdfz.education.domain.EduPersonPo;
import com.han.system.sdfz.education.domain.EduRoomPo;
import com.han.system.sdfz.education.domain.EduSchoolPo;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import java.util.List;

/**
 * 在没有 Spring 容器的单测里注册实体元数据。
 *
 * <p>{@code LambdaQueryWrapper} 在 {@code eq(Entity::getField, ...)} 时就要解析列名，
 * 拿不到 TableInfo 会直接抛异常，所以纯 Mockito 测试必须先手工初始化一次。
 */
final class LegacyTableInfoBootstrap {

    private static boolean initialized;

    private LegacyTableInfoBootstrap() {
    }

    static synchronized void init() {
        if (initialized) {
            return;
        }
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        List<Class<?>> entities = List.of(
                EduSchoolPo.class, EduClassPo.class, EduPersonPo.class, EduPersonClassPo.class,
                EduDevicePo.class, EduRoomPo.class, SysUserPo.class, SysDictDataPo.class);
        entities.forEach(entity -> TableInfoHelper.initTableInfo(assistant, entity));
        initialized = true;
    }
}
