package com.han.auth.sdfz.digitalcampus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.han.common.core.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DigitalCampusClientTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    void fetchCurrentUserDecryptsAndKeepsOnlyAllowedRoles() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DigitalCampusClient client = new DigitalCampusClient(
                new ObjectMapper(), true, "https://campus.example/api/", "2,5", builder);

        String result = """
                {"code":200,"success":true,"result":{"wxPhoneNumber":"138****0000","roles":[
                  {"userId":"u1","userName":"teacher","identityId":"i1","identityName":"教师身份",
                   "roleType":"2","schoolId":"s1","schoolName":"测试学校","branchId":"c1","branchName":"一班",
                   "isSchool":"2","areaCode":"50010000",
                   "dutyType":[{"pkId":"d1","roleType":"9","positionName":"管理员","itemText":"管理员"}],
                   "classes":[{"branchId":"c1","branchName":"一班","schoolId":"s1","schoolName":"测试学校",
                     "schoolLevel":"3","areaCode":"50010000","countyEduDepartId":"500100","countyEduDepartName":"区教委"}]},
                  {"userId":"u1","identityId":"i2","roleType":"3","schoolId":"s1"}
                ]}}
                """;
        String encrypted = DigitalCampusAesCodec.encrypt(result, TOKEN);
        String envelope = "{\"code\":2000,\"result\":\"" + encrypted + "\"}";

        server.expect(once(), requestTo("https://campus.example/api/user/user/getOneById"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("access-token", TOKEN))
                .andExpect(header("x-platform", DigitalCampusAesCodec.encrypt(DigitalCampusClient.CURRENT_USER_PATH, TOKEN)))
                .andRespond(withSuccess(envelope, MediaType.APPLICATION_JSON));

        DigitalCampusProfile profile = client.fetchCurrentUser(TOKEN);

        assertThat(profile.phone()).isEqualTo("138****0000");
        assertThat(profile.identities()).hasSize(1);
        assertThat(profile.identities().getFirst().identityId()).isEqualTo("i1");
        assertThat(profile.identities().getFirst().duties()).extracting(DigitalCampusProfile.Duty::roleType)
                .containsExactly("9");
        assertThat(profile.identities().getFirst().classes()).extracting(DigitalCampusProfile.ClassMembership::branchId)
                .containsExactly("c1");
        server.verify();
    }

    @Test
    void selectIdentityRequiresExplicitChoiceWhenMultipleAreAvailable() {
        DigitalCampusProfile.Identity first = identity("i1");
        DigitalCampusProfile.Identity second = identity("i2");
        DigitalCampusProfile profile = new DigitalCampusProfile("", java.util.List.of(first, second));

        assertThat(profile.selectIdentity("i2")).isEqualTo(second);
        assertThatThrownBy(() -> profile.selectIdentity(""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请选择数字校园登录身份");
        assertThatThrownBy(() -> profile.selectIdentity("missing"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("指定的数字校园身份不可用");
    }

    @Test
    void rejectsDisabledClientAndMalformedTokenWithoutCallingUpstream() {
        DigitalCampusClient disabled = new DigitalCampusClient(
                new ObjectMapper(), false, "https://campus.example/api", "2,5", RestClient.builder());
        assertThatThrownBy(() -> disabled.fetchCurrentUser(TOKEN))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数字校园登录未配置");

        DigitalCampusClient enabled = new DigitalCampusClient(
                new ObjectMapper(), true, "https://campus.example/api", "2,5", RestClient.builder());
        assertThatThrownBy(() -> enabled.fetchCurrentUser("short-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("数字校园 Token 格式无效");
    }

    private DigitalCampusProfile.Identity identity(String identityId) {
        return new DigitalCampusProfile.Identity(
                "u1", "name", identityId, "identity", "2", "s1", "school",
                "", "", "2", "50010000", java.util.List.of(), java.util.List.of());
    }
}
