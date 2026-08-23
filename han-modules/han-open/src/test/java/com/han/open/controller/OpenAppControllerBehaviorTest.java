package com.han.open.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.constant.Constants;
import com.han.common.core.domain.PageResult;
import com.han.common.core.domain.R;
import com.han.common.core.exception.BusinessException;
import com.han.open.domain.dto.OpenAppDTO;
import com.han.open.domain.dto.OpenAppStatusUpdateRequest;
import com.han.open.domain.po.OpenAppPo;
import com.han.open.domain.query.OpenAppQuery;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OpenAppController} 行为测试：仅 mock {@link IOpenAppService}，
 * {@link ObjectMapper} 使用真实实例，覆盖 list/edit/remove/resetSecret/changeStatus。
 */
class OpenAppControllerBehaviorTest {

    private IOpenAppService openAppService;
    private OpenAppController controller;

    @BeforeEach
    void setUp() {
        openAppService = mock(IOpenAppService.class);
        controller = new OpenAppController(new ObjectMapper(), openAppService);
    }

    @Test
    void listReturnsPagedResultAndPassesQueryThrough() {
        OpenAppQuery query = new OpenAppQuery();
        query.setPageNum(1);
        query.setPageSize(10);
        query.setAppName("测试应用");
        query.setAppType("web");
        query.setStatus(0);
        query.setVendorId(99L);

        OpenAppVO vo = new OpenAppVO();
        vo.setAppId(1L);
        vo.setAppName("测试应用");
        PageResult<OpenAppVO> page = PageResult.of(List.of(vo), 1L, 1, 10);
        when(openAppService.selectPage(query)).thenReturn(page);

        R<PageResult<OpenAppVO>> result = controller.list(query);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(result.getData()).isSameAs(page);
        assertThat(result.getData().getRows()).hasSize(1);
        assertThat(result.getData().getRows().get(0).getAppName()).isEqualTo("测试应用");

        ArgumentCaptor<OpenAppQuery> captor = ArgumentCaptor.forClass(OpenAppQuery.class);
        verify(openAppService).selectPage(captor.capture());
        OpenAppQuery passed = captor.getValue();
        assertThat(passed).isSameAs(query);
        assertThat(passed.getAppName()).isEqualTo("测试应用");
        assertThat(passed.getAppType()).isEqualTo("web");
        assertThat(passed.getStatus()).isEqualTo(0);
        assertThat(passed.getVendorId()).isEqualTo(99L);
    }

    @Test
    void editConvertsBodyAndBindsAppId() {
        Map<String, Object> body = mapOf("appId", 100L, "appName", "foo");

        R<Void> result = controller.edit(body);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);

        ArgumentCaptor<OpenAppDTO> captor = ArgumentCaptor.forClass(OpenAppDTO.class);
        verify(openAppService).update(captor.capture());
        OpenAppDTO dto = captor.getValue();
        assertThat(dto.getAppId()).isEqualTo(100L);
        assertThat(dto.getBase()).isNotNull();
        assertThat(dto.getBase().getAppName()).isEqualTo("foo");
    }

    @Test
    void editFallsBackToIdFieldForCompatibility() {
        Map<String, Object> body = mapOf("id", 200L, "appName", "bar");

        R<Void> result = controller.edit(body);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);

        ArgumentCaptor<OpenAppDTO> captor = ArgumentCaptor.forClass(OpenAppDTO.class);
        verify(openAppService).update(captor.capture());
        OpenAppDTO dto = captor.getValue();
        assertThat(dto.getAppId()).isEqualTo(200L);
        assertThat(dto.getBase().getAppName()).isEqualTo("bar");
    }

    @Test
    void editPropagatesServiceExceptionWhenAppIdMissing() {
        when(openAppService.update(any(OpenAppDTO.class)))
                .thenThrow(new BusinessException("应用ID不能为空"));

        assertThatThrownBy(() -> controller.edit(mapOf("appName", "x")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用ID不能为空");

        ArgumentCaptor<OpenAppDTO> captor = ArgumentCaptor.forClass(OpenAppDTO.class);
        verify(openAppService).update(captor.capture());
        assertThat(captor.getValue().getAppId()).isNull();
    }

    @Test
    void removeDeletesById() {
        R<Void> result = controller.remove(100L);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);
        verify(openAppService).deleteById(100L);
    }

    @Test
    void removePropagatesServiceException() {
        when(openAppService.deleteById(100L))
                .thenThrow(new BusinessException("应用不存在"));

        assertThatThrownBy(() -> controller.remove(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("应用不存在");
    }

    @Test
    void resetSecretReturnsNewSecret() {
        when(openAppService.resetAppSecret(100L)).thenReturn("sk_new_secret");

        R<String> result = controller.resetSecret(100L);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);
        assertThat(result.getData()).isEqualTo("sk_new_secret");
        verify(openAppService).resetAppSecret(100L);
    }

    @Test
    void changeStatusResolvesBaseStatusAndCallsService() {
        OpenAppStatusUpdateRequest request = new OpenAppStatusUpdateRequest();
        request.setAppId(100L);
        OpenAppPo base = new OpenAppPo();
        base.setStatus(1);
        request.setBase(base);

        R<Void> result = controller.changeStatus(request);

        assertThat(result.getCode()).isEqualTo(Constants.SUCCESS);
        verify(openAppService).updateStatus(100L, 1);
    }

    @Test
    void changeStatusPropagatesServiceExceptionWhenStatusIllegal() {
        OpenAppStatusUpdateRequest request = new OpenAppStatusUpdateRequest();
        request.setAppId(100L);

        assertThat(request.resolveStatus()).isNull();
        doThrow(new BusinessException("状态参数不合法"))
                .when(openAppService).updateStatus(100L, null);

        assertThatThrownBy(() -> controller.changeStatus(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("状态参数不合法");
    }

    private static Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((String) entries[i], entries[i + 1]);
        }
        return map;
    }
}
