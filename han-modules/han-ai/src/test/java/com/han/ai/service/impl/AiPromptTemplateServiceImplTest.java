package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.ai.mapper.AiPromptTemplateMapper;
import com.han.common.core.domain.PageResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiPromptTemplateServiceImplTest {

    @Test
    void selectPageSkipsGlobalTenantInterceptorSoBuiltInTemplatesRemainVisible() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo template = new AiPromptTemplatePo();
        template.setTemplateId(1L);
        template.setTemplateName("通用文本生成示例");
        template.setBuiltIn(1);

        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine("com.han.ai.mapper.AiPromptTemplateMapper.selectPage")).isTrue();
            Page<AiPromptTemplatePo> page = invocation.getArgument(0);
            page.setRecords(List.of(template));
            page.setTotal(1);
            return page;
        });

        PageResult<AiPromptTemplatePo> result = service.selectPage(new AiPromptTemplateQuery());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRows()).extracting(AiPromptTemplatePo::getTemplateName).containsExactly("通用文本生成示例");
    }

    @Test
    void selectAllSkipsGlobalTenantInterceptorSoBuiltInTemplatesRemainVisible() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo template = new AiPromptTemplatePo();
        template.setTemplateId(1L);
        template.setTemplateName("通用文本生成示例");
        template.setBuiltIn(1);

        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine("com.han.ai.mapper.AiPromptTemplateMapper.selectList")).isTrue();
            return List.of(template);
        });

        List<AiPromptTemplatePo> result = service.selectAll();

        assertThat(result).extracting(AiPromptTemplatePo::getTemplateName).containsExactly("通用文本生成示例");
    }

    @Test
    void selectPageBackfillsBuiltInTemplatesWhenDatabaseMissedSeed() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);

        when(mapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<AiPromptTemplatePo> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

        service.selectPage(new AiPromptTemplateQuery());

        ArgumentCaptor<AiPromptTemplatePo> captor = ArgumentCaptor.forClass(AiPromptTemplatePo.class);
        verify(mapper, atLeastOnce()).insert(captor.capture());
        List<AiPromptTemplatePo> inserted = captor.getAllValues();
        assertThat(inserted).hasSizeGreaterThanOrEqualTo(2);
        assertThat(inserted).extracting(AiPromptTemplatePo::getCategory)
                .contains("general_text", "general_summary");
        assertThat(inserted).extracting(AiPromptTemplatePo::getContent)
                .anySatisfy(content -> assertThat(content).contains("{{input}}"));
    }

    @Test
    void selectAllRefreshesStaleBuiltInTemplatesWhenSqlTemplateContentIsOld() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo stale = new AiPromptTemplatePo();
        stale.setTemplateId(99L);
        stale.setTemplateName("通用内容总结示例");
        stale.setCategory("general_text");
        stale.setContent("old prompt");
        stale.setVariables("[]");
        stale.setDescription("old");
        stale.setBuiltIn(0);
        stale.setStatus("1");

        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(stale));

        service.selectAll();

        verify(mapper, atLeastOnce()).updateById(any(AiPromptTemplatePo.class));
        assertThat(stale.getBuiltIn()).isEqualTo(1);
        assertThat(stale.getStatus()).isEqualTo("0");
        assertThat(stale.getContent()).contains("待总结内容");
    }

    @Test
    void builtInBackfillDoesNotOverwriteTenantOwnedTemplateWithSameName() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo custom = new AiPromptTemplatePo();
        custom.setTemplateId(101L);
        custom.setTenantId(9L);
        custom.setTemplateName("通用内容总结示例");
        custom.setCategory("custom");
        custom.setContent("tenant custom prompt");
        custom.setBuiltIn(0);
        custom.setStatus("0");

        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(custom));

        service.selectAll();

        verify(mapper, never()).updateById(same(custom));
        assertThat(custom.getCategory()).isEqualTo("custom");
        assertThat(custom.getContent()).isEqualTo("tenant custom prompt");
        assertThat(custom.getBuiltIn()).isEqualTo(0);
    }
}
