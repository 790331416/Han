package com.han.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.plugins.InterceptorIgnoreHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.han.ai.domain.po.AiPromptTemplatePo;
import com.han.ai.domain.query.AiPromptTemplateQuery;
import com.han.ai.mapper.AiPromptTemplateMapper;
import com.han.common.core.domain.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiPromptTemplateServiceImplTest {

    @Test
    void selectPageSkipsGlobalTenantInterceptorSoBuiltInTemplatesRemainVisible() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo template = new AiPromptTemplatePo();
        template.setTemplateId(1L);
        template.setTemplateName("AI短剧剧本生成");
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
        assertThat(result.getRows()).extracting(AiPromptTemplatePo::getTemplateName).containsExactly("AI短剧剧本生成");
    }

    @Test
    void selectAllSkipsGlobalTenantInterceptorSoBuiltInTemplatesRemainVisible() {
        AiPromptTemplateMapper mapper = mock(AiPromptTemplateMapper.class);
        AiPromptTemplateServiceImpl service = new AiPromptTemplateServiceImpl(mapper);
        AiPromptTemplatePo template = new AiPromptTemplatePo();
        template.setTemplateId(1L);
        template.setTemplateName("AI短剧剧本生成");
        template.setBuiltIn(1);

        when(mapper.selectList(any(Wrapper.class))).thenAnswer(invocation -> {
            assertThat(InterceptorIgnoreHelper.hasIgnoreStrategy()).isTrue();
            assertThat(InterceptorIgnoreHelper.willIgnoreTenantLine("com.han.ai.mapper.AiPromptTemplateMapper.selectList")).isTrue();
            return List.of(template);
        });

        List<AiPromptTemplatePo> result = service.selectAll();

        assertThat(result).extracting(AiPromptTemplatePo::getTemplateName).containsExactly("AI短剧剧本生成");
    }
}
