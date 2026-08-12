package com.han.system.sdfz.compat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyProtocolTest {

    // ------------------------------------------------------------ param 提取

    @Test
    void readsParamFromGetRequestBodyBecauseLegacyApiSendsGetWithBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/user/identity/getIdentityBypkId");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("param=abcdef0123".getBytes(StandardCharsets.UTF_8));

        assertThat(LegacyProtocol.extractParam(request)).isEqualTo("abcdef0123");
    }

    @Test
    void readsParamFromQueryStringWhenThereIsNoBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/user/org/getById");
        request.setParameter("param", "queryvalue");

        assertThat(LegacyProtocol.extractParam(request)).isEqualTo("queryvalue");
    }

    @Test
    void readsParamFromPostFormBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdfz-compat/user/user/login");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("param=formvalue&other=1".getBytes(StandardCharsets.UTF_8));

        assertThat(LegacyProtocol.extractParam(request)).isEqualTo("formvalue");
    }

    @Test
    void readsParamFromJsonBody() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdfz-compat/user/user/login");
        request.setContentType("application/json");
        request.setContent("{\"param\":\"jsonvalue\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(LegacyProtocol.extractParam(request)).isEqualTo("jsonvalue");
    }

    @Test
    void prefersBodyOverQueryStringWhenBothCarryParam() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/user/org/getById");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("param=frombody".getBytes(StandardCharsets.UTF_8));
        request.setParameter("param", "fromquery");

        assertThat(LegacyProtocol.extractParam(request)).isEqualTo("frombody");
    }

    @Test
    void returnsNullWhenNoParamIsPresentAnywhere() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdfz-compat/user/user/getOneById");

        assertThat(LegacyProtocol.extractParam(request)).isNull();
    }

    // ------------------------------------------------------------ 明文参数解析

    @Test
    void parsesPlainQueryStringWithoutTouchingPlusSigns() {
        assertThat(LegacyProtocol.parseQueryString("orgId=100&orgType=&code=a+b"))
                .containsEntry("orgId", "100")
                .containsEntry("orgType", "")
                .containsEntry("code", "a+b");
    }

    @Test
    void percentDecodesOnlyValuesThatLookEncoded() {
        assertThat(LegacyProtocol.parseQueryString("orgName=%E5%85%B0%E5%B7%9E&raw=100%"))
                .containsEntry("orgName", "兰州")
                .containsEntry("raw", "100%");
    }

    @Test
    void keepsFirstValueForRepeatedKeysAndSkipsEmptyPairs() {
        assertThat(LegacyProtocol.parseQueryString("&a=1&&a=2&"))
                .containsExactly(java.util.Map.entry("a", "1"));
    }

    // ------------------------------------------------------------ 消费者判别

    @Test
    void treatsGetOnChannelBPathAsLegacyApi() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/" + LegacyPaths.DEVICE_LIST);

        assertThat(LegacyProtocol.detectConsumer(request, LegacyPaths.DEVICE_LIST))
                .isEqualTo(LegacyProtocol.Consumer.LEGACY_API);
    }

    @Test
    void treatsCheckCodeHeaderAsTheLegacyFrontendEvenOnASharedPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/" + LegacyPaths.DEVICE_LIST);
        request.addHeader(LegacyProtocol.CHECK_CODE_HEADER, "");

        assertThat(LegacyProtocol.detectConsumer(request, LegacyPaths.DEVICE_LIST))
                .isEqualTo(LegacyProtocol.Consumer.LEGACY_UI);
    }

    @Test
    void treatsPostAsTheLegacyFrontendBecauseLegacyApiOnlySendsGet() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sdfz-compat/" + LegacyPaths.SELECT_PLACE);

        assertThat(LegacyProtocol.detectConsumer(request, LegacyPaths.SELECT_PLACE))
                .isEqualTo(LegacyProtocol.Consumer.LEGACY_UI);
    }

    @Test
    void treatsChannelCOnlyPathAsTheLegacyFrontend() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sdfz-compat/" + LegacyPaths.UI_DICT_ITEMS);

        assertThat(LegacyProtocol.detectConsumer(request, LegacyPaths.UI_DICT_ITEMS))
                .isEqualTo(LegacyProtocol.Consumer.LEGACY_UI);
    }

    @Test
    void keepsAllFifteenChannelBPathsOnTheBareObjectContract() {
        List<String> channelB = List.of(
                LegacyPaths.USER_INFO_GET_BY_ID, LegacyPaths.USER_INFO_GET_USER_INFO,
                LegacyPaths.IDENTITY_GET_BY_PK_ID, LegacyPaths.ORG_CHILD_LIST, LegacyPaths.ORG_GET_BY_ID,
                LegacyPaths.ORG_LIST_BY_PAGE, LegacyPaths.ORG_SCHOOL_INFO,
                LegacyPaths.MANAGER_ORG_INFO_FOR_EXTERNAL, LegacyPaths.MANAGER_LAZY_ORG_TREE,
                LegacyPaths.MANAGER_ORG_BRANCH_TREE, LegacyPaths.PINYIN_ORG_RESULT,
                LegacyPaths.MANAGER_TEACHER_LIST, LegacyPaths.SELECT_PLACE,
                LegacyPaths.DEVICE_LIST, LegacyPaths.DEVICE_BY_CODE);

        assertThat(channelB).hasSize(15).doesNotHaveDuplicates();
        assertThat(channelB).allSatisfy(path -> assertThat(LegacyProtocol.detectConsumer(
                new MockHttpServletRequest("GET", "/sdfz-compat/" + path), path))
                .isEqualTo(LegacyProtocol.Consumer.LEGACY_API));
    }

    @Test
    void readsTokenFromHeaderFirstThenQueryParam() {
        MockHttpServletRequest withHeader = new MockHttpServletRequest("GET", "/sdfz-compat/x");
        withHeader.addHeader(LegacyProtocol.TOKEN_HEADER, " header-token ");
        MockHttpServletRequest withQuery = new MockHttpServletRequest("GET", "/sdfz-compat/x");
        withQuery.setParameter(LegacyProtocol.TOKEN_HEADER, "query-token");

        assertThat(LegacyProtocol.token(withHeader)).isEqualTo("header-token");
        assertThat(LegacyProtocol.token(withQuery)).isEqualTo("query-token");
        assertThat(LegacyProtocol.token(new MockHttpServletRequest("GET", "/sdfz-compat/x"))).isNull();
    }
}
