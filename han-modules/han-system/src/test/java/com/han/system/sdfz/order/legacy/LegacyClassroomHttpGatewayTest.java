package com.han.system.sdfz.order.legacy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 旧 api 内部接口通道的信封处理。
 *
 * <p>这组端点旧 api 侧还没实现，本测试锁的是<b>契约</b>：等对方交付时按这里的形状对齐即可。
 * 重点验证失败分级——网络层错误算可重试，业务码错误算不可重试，两者进的是完全不同的重试队列。</p>
 */
@DisplayName("三课堂内部接口通道")
class LegacyClassroomHttpGatewayTest {

    private static final String BASE = "http://legacy-api.invalid/inner/";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private LegacyClassroomHttpGateway gateway;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        gateway = new LegacyClassroomHttpGateway(builder.build(), BASE, "inner-token");
    }

    @Test
    @DisplayName("课程列表按约定的 code/result 信封解析，并带上内部鉴权头")
    void parsesCourseList() {
        server.expect(requestTo(BASE + "internal/course-attend/courses"
                        + "?classId=2002&from=2026-09-01T00:00:00&to=2027-01-15T23:59:59"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-Han-Inner-Token", "inner-token"))
                .andRespond(withSuccess("""
                        {"code":200,"result":[
                          {"courseId":"C1","courseName":"语文第一课","courseType":"1",
                           "classId":"2002","className":"主讲一班","organId":"1002","organName":"主讲学校",
                           "subjectCode":"YW","subjectName":"语文",
                           "timeBegin":"2026-10-10T08:00:00","timeEnd":"2026-10-10T08:45:00"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<LegacyCourse> courses = gateway.listCourses("2002",
                LocalDateTime.of(2026, 9, 1, 0, 0, 0),
                LocalDateTime.of(2027, 1, 15, 23, 59, 59));

        assertThat(courses).hasSize(1);
        LegacyCourse course = courses.getFirst();
        assertThat(course.courseId()).isEqualTo("C1");
        assertThat(course.subjectCode()).isEqualTo("YW");
        assertThat(course.timeBegin()).isEqualTo(LocalDateTime.of(2026, 10, 10, 8, 0));
        server.verify();
    }

    @Test
    @DisplayName("对端沿用旧库的空格时间格式也能解析")
    void alsoParsesLegacyTimeFormat() {
        server.expect(requestTo(BASE + "internal/course-attend/course?courseId=C1"))
                .andRespond(withSuccess("""
                        {"code":200,"result":{"courseId":"C1","timeBegin":"2026-10-10 08:00:00"}}
                        """, MediaType.APPLICATION_JSON));

        LegacyCourse course = gateway.findCourse("C1");

        assertThat(course).isNotNull();
        assertThat(course.timeBegin()).isEqualTo(LocalDateTime.of(2026, 10, 10, 8, 0));
    }

    @Test
    @DisplayName("物化成功时返回 attendId")
    void returnsAttendId() {
        server.expect(requestTo(BASE + "internal/course-attend/materialize"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"code\":200,\"result\":{\"attendId\":\"A-1\"}}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.materializeAttend(sampleRequest())).isEqualTo("A-1");
        server.verify();
    }

    @Test
    @DisplayName("业务码不是 200 时算不可重试，不消耗重试次数")
    void businessFailureIsNotRetryable() {
        server.expect(requestTo(BASE + "internal/course-attend/materialize"))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"课程不存在\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.materializeAttend(sampleRequest()))
                .isInstanceOf(LegacyClassroomException.class)
                .satisfies(ex -> assertThat(((LegacyClassroomException) ex).isRetryable()).isFalse())
                .hasMessageContaining("课程不存在");
    }

    @Test
    @DisplayName("网络层失败算可重试：旧 api 重启、网关抖动都属于这一类")
    void transportFailureIsRetryable() {
        server.expect(requestTo(BASE + "internal/course-attend/materialize"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> gateway.materializeAttend(sampleRequest()))
                .isInstanceOf(LegacyClassroomException.class)
                .satisfies(ex -> assertThat(((LegacyClassroomException) ex).isRetryable()).isTrue());
    }

    @Test
    @DisplayName("响应里没有 attendId 时明确报错，不返回空串让台账记成已物化")
    void rejectsMissingAttendId() {
        server.expect(requestTo(BASE + "internal/course-attend/materialize"))
                .andRespond(withSuccess("{\"code\":200,\"result\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> gateway.materializeAttend(sampleRequest()))
                .isInstanceOf(LegacyClassroomException.class)
                .hasMessageContaining("attendId");
    }

    @Test
    @DisplayName("未配置通道时一律报不可重试，不会把台账刷成重试队列")
    void disabledGatewayFailsFast() {
        DisabledLegacyClassroomGateway disabled = new DisabledLegacyClassroomGateway();

        assertThatThrownBy(() -> disabled.materializeAttend(sampleRequest()))
                .isInstanceOf(LegacyClassroomException.class)
                .satisfies(ex -> assertThat(((LegacyClassroomException) ex).isRetryable()).isFalse());
    }

    private static LegacyAttendRequest sampleRequest() {
        return new LegacyAttendRequest("C1", "1", "1001", "听讲学校", "2001", "听讲一班",
                "500000", "", "500100", "", "500103", "",
                "3001", "录播教室", "", "", "DEV-01", "听讲端一体机");
    }
}
