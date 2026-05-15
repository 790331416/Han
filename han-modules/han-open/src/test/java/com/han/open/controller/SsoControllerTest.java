package com.han.open.controller;

import com.han.common.core.constant.Constants;
import com.han.common.core.domain.R;
import com.han.open.service.IOpenAppService;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoControllerTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = mock(HashOperations.class);
    private final IOpenAppService openAppService = mock(IOpenAppService.class);
    private final SsoController controller = new SsoController(redisTemplate, openAppService);

    @Test
    @SuppressWarnings("unchecked")
    void validateTicketConsumesValidTicket() {
        when(openAppService.validateClient("app", "secret")).thenReturn(true);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("han:sso:ticket:ST-test")).thenReturn(Map.of(
                "userId", "10",
                "clientId", "app",
                "redirectUri", "https://client.test/callback",
                "state", "xyz"
        ));

        R<Object> response = controller.validateTicket("ST-test", "app", "secret");

        assertThat(response.getCode()).isEqualTo(Constants.SUCCESS);
        Map<String, Object> payload = (Map<String, Object>) response.getData();
        assertThat(payload.get("userId")).isEqualTo(10L);
        assertThat(payload.get("clientId")).isEqualTo("app");
        verify(redisTemplate).delete("han:sso:ticket:ST-test");
    }

    @Test
    void validateTicketRejectsInvalidClient() {
        when(openAppService.validateClient("app", "bad")).thenReturn(false);

        R<Object> response = controller.validateTicket("ST-test", "app", "bad");

        assertThat(response.getCode()).isNotEqualTo(Constants.SUCCESS);
    }
}
