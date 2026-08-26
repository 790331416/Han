package com.han.open.controller;

import com.han.api.system.SystemServiceClient;
import com.han.common.core.domain.R;
import com.han.open.domain.vo.OpenAppVO;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAppSchoolControllerTest {

    @Test
    void resolvesNamesOnlyAfterApplicationOwnershipCheck() {
        IOpenAppService appService = mock(IOpenAppService.class);
        SystemServiceClient system = mock(SystemServiceClient.class);
        OpenAppVO app = new OpenAppVO();
        app.setTenantId(99L);
        app.setSchoolIds(List.of(1001L, 1002L));
        when(appService.selectVoById(201L)).thenReturn(app);
        when(system.getOpenSchoolNames(99L, app.getSchoolIds()))
                .thenReturn(R.ok(Map.of(1001L, "鲁巴数智教育中心", 1002L, "两江中学")));

        assertThat(new OpenAppSchoolController(appService, system).schoolNames(201L).getData())
                .containsEntry(1001L, "鲁巴数智教育中心")
                .containsEntry(1002L, "两江中学");
    }
}
