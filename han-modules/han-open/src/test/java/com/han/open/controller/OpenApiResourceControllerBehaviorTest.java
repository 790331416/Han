package com.han.open.controller;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.po.OpenApiResourcePo;
import com.han.open.domain.vo.OpenApiResourceDetailVO;
import com.han.open.domain.vo.OpenApiResourceVersionVO;
import com.han.open.mapper.OpenApiResourceMapper;
import com.han.open.service.OpenApiResourceService;
import com.han.open.service.ResourcePathMappingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpenApiResourceController 行为测试：覆盖 offline / getDetail / deprecateVersion
 * 三个 handler 的正例与负例（纯单元测试，只 mock 协作的 mapper / service）。
 *
 * <p>注意：类上的 {@code @AdminAuth} 与各 handler 上的 {@code @PreAuthorize} 属于 AOP 权限切面，
 * 纯单元测试不会触发这些注解逻辑，此处仅覆盖 controller 方法体内的业务分支。</p>
 */
class OpenApiResourceControllerBehaviorTest {

    private OpenApiResourceMapper resourceMapper;
    private OpenApiResourceService resourceService;
    private ResourcePathMappingService pathMappingService;
    private OpenApiResourceController controller;

    @BeforeEach
    void setUp() {
        resourceMapper = mock(OpenApiResourceMapper.class);
        resourceService = mock(OpenApiResourceService.class);
        pathMappingService = mock(ResourcePathMappingService.class);
        controller = new OpenApiResourceController(resourceMapper, resourceService, pathMappingService);
    }

    @Test
    void offlineMarksResourceOfflineAndRefreshesCache() {
        when(resourceMapper.updateById(org.mockito.ArgumentMatchers.any(OpenApiResourcePo.class))).thenReturn(1);

        R<Void> response = controller.offline(7L);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getMsg()).isEqualTo("操作成功");

        ArgumentCaptor<OpenApiResourcePo> captor = ArgumentCaptor.forClass(OpenApiResourcePo.class);
        verify(resourceMapper).updateById(captor.capture());
        OpenApiResourcePo update = captor.getValue();
        assertThat(update.getId()).isEqualTo(7L);
        assertThat(update.getStatus()).isEqualTo(1);
        assertThat(update.getPublishStatus()).isEqualTo(3);
        verify(pathMappingService).refreshCache();
    }

    @Test
    void getDetailReturnsResourceDetail() {
        OpenApiResourceDetailVO detail = new OpenApiResourceDetailVO();
        detail.setId(7L);
        detail.setResourceName("教师目录");
        when(resourceService.getDetail(7L)).thenReturn(detail);

        R<OpenApiResourceDetailVO> response = controller.getDetail(7L);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isSameAs(detail);
        verify(resourceService).getDetail(7L);
    }

    @Test
    void getDetailPropagatesBusinessExceptionWhenResourceMissing() {
        when(resourceService.getDetail(999L)).thenThrow(new BusinessException("资源不存在"));

        assertThatThrownBy(() -> controller.getDetail(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("资源不存在");
    }

    @Test
    void deprecateVersionReturnsDeprecatedVersionAndRefreshesCache() {
        OpenApiResourceVersionVO deprecated = new OpenApiResourceVersionVO();
        deprecated.setId(11L);
        deprecated.setStatus(2);
        when(resourceService.deprecateVersion(11L)).thenReturn(deprecated);

        R<OpenApiResourceVersionVO> response = controller.deprecateVersion(11L);

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(response.getData()).isSameAs(deprecated);
        verify(resourceService).deprecateVersion(11L);
        verify(pathMappingService).refreshCache();
    }

    @Test
    void deprecateVersionPropagatesServiceExceptionWithoutRefreshingCache() {
        when(resourceService.deprecateVersion(11L))
                .thenThrow(new BusinessException("仅已发布版本可以废弃"));

        assertThatThrownBy(() -> controller.deprecateVersion(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("仅已发布版本可以废弃");
        verify(pathMappingService, never()).refreshCache();
    }
}
